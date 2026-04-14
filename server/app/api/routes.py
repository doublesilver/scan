import asyncio
import io
import logging
import os
import time
import uuid
from pathlib import Path

from fastapi import APIRouter, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, Response
from PIL import Image

from app.db.database import get_db, get_read_db
from app.db.database import write_lock as _write_lock
from app.models.schemas import (
    BoxResponse,
    CartRequest,
    FavoriteItem,
    FavoriteRequest,
    HistoryItem,
    PrintRequest,
    RecentScanItem,
    ScanResponse,
    SearchResponse,
    StockLogItem,
    StockResponse,
    StockUpdate,
)
from app.services.box_service import add_member as _add_member
from app.services.box_service import create_box as _create_box
from app.services.box_service import get_box as _get_box
from app.services.box_service import remove_member as _remove_member
from app.services.box_service import update_box as _update_box
from app.services.cart_db_service import add_cart_item as _add_cart_item
from app.services.cart_db_service import clear_cart as _clear_cart
from app.services.cart_db_service import delete_cart_item as _delete_cart_item
from app.services.cart_db_service import get_cart_items as _get_cart_items
from app.services.cart_db_service import update_cart_item as _update_cart_item
from app.services.cart_service import add_to_cart as _add_to_cart
from app.services.favorite_service import add_favorite as _add_favorite
from app.services.favorite_service import get_favorites as _get_favorites
from app.services.favorite_service import remove_favorite as _remove_favorite
from app.services.history_service import get_history as _get_history
from app.services.history_service import log_action as _log_action
from app.services.image_service import get_image_data, guess_media_type
from app.services.print_log_service import log_print_attempt as _log_print_attempt
from app.services.print_service import call_print_agent as _call_print_agent
from app.services.print_service import print_label as _print_label
from app.services.product_service import scan_barcode as _scan_barcode
from app.services.product_service import search_products as _search_products
from app.services.scan_log_service import get_recent_scans as _get_recent_scans
from app.services.scan_log_service import log_scan as _log_scan
from app.services.status_service import get_status as _get_status
from app.services.stock_service import get_stock as _get_stock
from app.services.stock_service import get_stock_log as _get_stock_log
from app.services.stock_service import update_stock as _update_stock
from app.services.url_import_service import import_purchase_urls as _import_purchase_urls

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api")

APP_VERSION_CODE = 90
APP_VERSION_NAME = "5.4.3"


@router.get("/scan/{barcode}", response_model=ScanResponse)
async def scan_barcode(barcode: str) -> ScanResponse:
    start = time.perf_counter()
    db = await get_read_db()

    result = await _scan_barcode(db, barcode)
    if result is None:
        raise HTTPException(status_code=404, detail="barcode not found")

    elapsed = (time.perf_counter() - start) * 1000
    logger.info("scan %s → %s (%.1fms)", barcode, result.sku_id or barcode, elapsed)

    if result.sku_id:
        write_db = await get_db()
        async with _write_lock:
            await _log_scan(write_db, barcode, result.sku_id, result.product_name)

    return result


@router.get("/search", response_model=SearchResponse)
async def search_products(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100),
) -> SearchResponse:
    db = await get_read_db()
    items = await _search_products(db, q, limit)
    return SearchResponse(total=len(items), items=items)


@router.get("/stock/{sku_id}", response_model=StockResponse)
async def get_stock(sku_id: str) -> StockResponse:
    db = await get_read_db()
    result = await _get_stock(db, sku_id)
    if result is None:
        raise HTTPException(status_code=404, detail="SKU not found")
    return result


@router.patch("/stock/{sku_id}", response_model=StockResponse)
async def update_stock(sku_id: str, body: StockUpdate) -> StockResponse:
    db = await get_db()
    async with _write_lock:
        result = await _update_stock(db, sku_id, body)
    if result is None:
        raise HTTPException(status_code=404, detail="SKU not found")
    return result


@router.get("/stock/{sku_id}/log", response_model=list[StockLogItem])
async def get_stock_log(sku_id: str, limit: int = Query(20, ge=1, le=100)) -> list[StockLogItem]:
    db = await get_read_db()
    return await _get_stock_log(db, sku_id, limit)


@router.get("/status")
async def status():
    db = await get_read_db()
    return await _get_status(db)


@router.post("/print")
async def print_label(body: PrintRequest, request: Request):
    from app.config import settings as _settings

    if _settings.print_agent_url:
        result = await _call_print_agent(
            request.app.state.http_client,
            body.product_name,
            body.barcode,
            body.sku_id,
            body.quantity,
        )
    else:
        result = await asyncio.to_thread(
            _print_label, body.product_name, body.barcode, body.sku_id, body.quantity
        )

    db = await get_db()
    async with _write_lock:
        await _log_print_attempt(
            db,
            barcode=body.barcode,
            sku_id=body.sku_id,
            product_name=body.product_name,
            quantity=body.quantity,
            status=result.get("status", ""),
            via=result.get("via", ""),
            http_status=result.get("http_status"),
            elapsed_ms=result.get("elapsed_ms"),
            message=result.get("message", ""),
            raw_response=result.get("raw_response", ""),
        )

        if result["status"] == "error":
            raise HTTPException(status_code=500, detail=result["message"])

        await _log_action(db, "print", body.barcode, body.sku_id, body.product_name, body.quantity)
    return result


@router.post("/cart")
async def add_to_cart(body: CartRequest):
    try:
        result = await asyncio.wait_for(
            asyncio.to_thread(
                _add_to_cart, body.barcode, body.sku_id, body.product_name, body.quantity
            ),
            timeout=10.0,
        )
    except TimeoutError:
        logger.error("장바구니 구글시트 타임아웃")
        raise HTTPException(status_code=500, detail="시트 연동 실패")
    if result["status"] == "error":
        raise HTTPException(status_code=500, detail=result["message"])
    db = await get_db()
    async with _write_lock:
        await _log_action(db, "cart", body.barcode, body.sku_id, body.product_name, body.quantity)
    return result


@router.get("/image/{path:path}")
async def get_image(path: str, request: Request, width: int | None = Query(None)) -> Response:
    http_client = request.app.state.http_client
    image_bytes, file_path, resized_width = await get_image_data(path, width, http_client)

    media_type = guess_media_type(path)
    if file_path:
        return FileResponse(str(file_path), media_type=media_type)
    return Response(content=image_bytes, media_type=media_type)


@router.get("/box/{qr_code}", response_model=BoxResponse)
async def get_box(qr_code: str) -> BoxResponse:
    db = await get_read_db()
    result = await _get_box(db, qr_code)
    if result is None:
        raise HTTPException(status_code=404, detail="box not found")
    return result


@router.post("/box", response_model=BoxResponse)
async def create_box(request: Request) -> BoxResponse:
    body = await request.json()
    qr_code = body.get("qr_code")
    if not qr_code:
        raise HTTPException(status_code=400, detail="qr_code is required")
    raw_members = body.get("members", [])
    member_sku_ids = body.get("member_sku_ids", [])
    if not raw_members and member_sku_ids:
        raw_members = [{"sku_id": sid, "sku_name": sid} for sid in member_sku_ids]
    db = await get_db()
    async with _write_lock:
        result = await _create_box(
            db,
            qr_code=qr_code,
            box_name=body.get("box_name", ""),
            product_master_name=body.get("product_master_name", ""),
            location=body.get("location"),
            members=raw_members,
        )
    return result


@router.patch("/box/{qr_code}", response_model=BoxResponse)
async def update_box(qr_code: str, request: Request) -> BoxResponse:
    body = await request.json()
    db = await get_db()
    async with _write_lock:
        result = await _update_box(
            db,
            qr_code=qr_code,
            box_name=body.get("box_name"),
            product_master_name=body.get("product_master_name"),
            location=body.get("location"),
        )
    if result is None:
        raise HTTPException(status_code=404, detail="box not found")
    return result


@router.post("/box/{qr_code}/member")
async def add_box_member(qr_code: str, request: Request):
    body = await request.json()
    sku_id = body.get("sku_id")
    if not sku_id:
        raise HTTPException(status_code=400, detail="sku_id is required")
    sku_name = body.get("sku_name")
    if not sku_name:
        raise HTTPException(status_code=400, detail="sku_name is required")
    db = await get_db()
    async with _write_lock:
        found = await _add_member(
            db,
            qr_code=qr_code,
            sku_id=sku_id,
            sku_name=sku_name,
            barcode=body.get("barcode"),
            location=body.get("location"),
        )
    if not found:
        raise HTTPException(status_code=404, detail="box not found")
    return {"status": "ok"}


@router.delete("/box/{qr_code}/member/{sku_id}")
async def remove_box_member(qr_code: str, sku_id: str):
    db = await get_db()
    async with _write_lock:
        found = await _remove_member(db, qr_code=qr_code, sku_id=sku_id)
    if not found:
        raise HTTPException(status_code=404, detail="not found")
    return {"status": "ok"}


@router.get("/history", response_model=list[HistoryItem])
async def get_history(
    type: str | None = Query(None),
    limit: int = Query(50, ge=1, le=200),
) -> list[HistoryItem]:
    db = await get_read_db()
    return await _get_history(db, action_type=type, limit=limit)


@router.post("/favorite")
async def add_favorite(body: FavoriteRequest):
    db = await get_db()
    async with _write_lock:
        await _add_favorite(db, body.sku_id, body.product_name, body.barcode)
    return {"status": "ok"}


@router.delete("/favorite/{sku_id}")
async def remove_favorite(sku_id: str):
    db = await get_db()
    async with _write_lock:
        found = await _remove_favorite(db, sku_id)
    if not found:
        raise HTTPException(status_code=404, detail="favorite not found")
    return {"status": "ok"}


@router.get("/favorites", response_model=list[FavoriteItem])
async def get_favorites() -> list[FavoriteItem]:
    db = await get_read_db()
    return await _get_favorites(db)


@router.get("/recent", response_model=list[RecentScanItem])
async def get_recent_scans(limit: int = Query(20, ge=1, le=100)) -> list[RecentScanItem]:
    db = await get_read_db()
    return await _get_recent_scans(db, limit)


@router.post("/import/urls")
async def import_urls(file_path: str = Query(...)):
    from app.config import settings as _settings

    base = Path(_settings.xlsx_watch_dir).resolve()
    target = (base / file_path).resolve()
    if not str(target).startswith(str(base) + "/") and target != base:
        raise HTTPException(status_code=400, detail="invalid file path")
    db = await get_db()
    async with _write_lock:
        result = await _import_purchase_urls(db, str(target))
    if result["status"] == "error":
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.post("/import/urls-upload")
async def import_urls_upload(request: Request):
    content_type = request.headers.get("content-type", "")
    if "multipart/form-data" not in content_type:
        raise HTTPException(status_code=400, detail="multipart/form-data required")

    form = await request.form()
    file = form.get("file")
    if not file:
        raise HTTPException(status_code=400, detail="file is required")

    import tempfile

    file_bytes = await file.read()
    with tempfile.NamedTemporaryFile(suffix=".xlsx", delete=False) as tmp:
        tmp.write(file_bytes)
        tmp_path = tmp.name

    db = await get_db()
    async with _write_lock:
        result = await _import_purchase_urls(db, tmp_path)

    Path(tmp_path).unlink(missing_ok=True)

    if result["status"] == "error":
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.get("/app-version")
async def app_version():
    return {
        "versionCode": APP_VERSION_CODE,
        "versionName": APP_VERSION_NAME,
        "downloadUrl": "/apk/app-live-debug.apk",
        "releaseNotes": "셀 상세 단순화, 상품마스터 이미지 업로드, 상품 편집/실사진 촬영",
        "forceUpdate": False,
    }


@router.patch("/product/{sku_id}/location")
async def update_product_location(sku_id: str, request: Request):
    body = await request.json()
    location = body.get("location", "")
    db = await get_db()
    async with _write_lock:
        result = await db.execute(
            "UPDATE product SET location = ? WHERE sku_id = ?", (location, sku_id)
        )
        await db.commit()
    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="product not found")
    return {"status": "ok", "location": location}


# ── 상품 목록 (페이지네이션) ──


@router.get("/products/export-csv")
async def export_products_csv(q: str = Query("")):
    import csv
    import io

    db = await get_read_db()
    params: list = []
    where = ""
    if q:
        where = " WHERE p.sku_id LIKE ? OR p.product_name LIKE ?"
        like = f"%{q}%"
        params = [like, like]

    rows = await (
        await db.execute(
            f"SELECT p.sku_id, p.product_name, p.category, p.brand, p.location, "
            f"p.purchase_url, p.naver_url, p.url_1688, p.flow_url "
            f"FROM product p{where} ORDER BY p.sku_id",
            params,
        )
    ).fetchall()

    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(
        [
            "SKU",
            "상품명",
            "카테고리",
            "브랜드",
            "위치",
            "쿠팡URL",
            "네이버URL",
            "1688URL",
            "FLOW_URL",
        ]
    )
    for r in rows:
        writer.writerow(r)

    return Response(
        content=buf.getvalue(),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=products.csv"},
    )


@router.get("/products")
async def list_products(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    q: str = Query(""),
    category: str = Query(""),
    brand: str = Query(""),
):
    db = await get_read_db()
    conditions = []
    params: list = []

    if q:
        conditions.append("(p.sku_id LIKE ? OR p.product_name LIKE ?)")
        like = f"%{q}%"
        params.extend([like, like])
    if category:
        conditions.append("p.category = ?")
        params.append(category)
    if brand:
        conditions.append("p.brand = ?")
        params.append(brand)

    where = (" WHERE " + " AND ".join(conditions)) if conditions else ""

    count_row = await (
        await db.execute(f"SELECT COUNT(*) FROM product p{where}", params)
    ).fetchone()
    total = count_row[0]

    offset = (page - 1) * size
    rows = await (
        await db.execute(
            f"SELECT p.sku_id, p.product_name, p.category, p.brand, "
            f"p.purchase_url, p.naver_url, p.url_1688, p.flow_url, p.location, "
            f"(SELECT b2.barcode FROM barcode b2 WHERE b2.sku_id = p.sku_id LIMIT 1) AS barcode, "
            f"(SELECT i.file_path FROM barcode b JOIN image i ON b.barcode = i.barcode "
            f"WHERE b.sku_id = p.sku_id ORDER BY i.sort_order LIMIT 1) AS thumbnail "
            f"FROM product p{where} ORDER BY p.sku_id LIMIT ? OFFSET ?",
            [*params, size, offset],
        )
    ).fetchall()

    items = [
        {
            "sku_id": r[0],
            "product_name": r[1],
            "category": r[2],
            "brand": r[3],
            "purchase_url": r[4],
            "naver_url": r[5],
            "url_1688": r[6],
            "flow_url": r[7],
            "location": r[8],
            "barcode": r[9],
            "thumbnail": r[10],
        }
        for r in rows
    ]
    return {"total": total, "page": page, "size": size, "items": items}


# ── 상품 범용 수정 ──


@router.patch("/product/{sku_id}")
async def update_product(sku_id: str, request: Request):
    body = await request.json()
    allowed = {
        "purchase_url",
        "naver_url",
        "url_1688",
        "flow_url",
        "location",
        "product_name",
        "category",
        "brand",
    }
    updates = {k: v for k, v in body.items() if k in allowed}
    if not updates:
        raise HTTPException(status_code=400, detail="no valid fields")

    sets = ", ".join(f"{k} = ?" for k in updates)
    vals = list(updates.values())
    vals.append(sku_id)

    db = await get_db()
    async with _write_lock:
        result = await db.execute(
            f"UPDATE product SET {sets}, updated_at = datetime('now') WHERE sku_id = ?",
            vals,
        )
        await db.commit()
    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="product not found")
    return {"status": "ok", **updates}


# ── 파싱 이력 ──


@router.get("/parse-logs")
async def get_parse_logs(limit: int = Query(50, ge=1, le=200)):
    db = await get_read_db()
    rows = await (
        await db.execute(
            "SELECT id, file_name, file_type, record_count, added_count, updated_count, "
            "skipped_count, error_count, duration_ms, parsed_at "
            "FROM parse_log ORDER BY parsed_at DESC LIMIT ?",
            (limit,),
        )
    ).fetchall()
    return [
        {
            "id": r[0],
            "file_name": r[1],
            "file_type": r[2],
            "record_count": r[3],
            "added_count": r[4],
            "updated_count": r[5],
            "skipped_count": r[6],
            "error_count": r[7],
            "duration_ms": r[8],
            "parsed_at": r[9],
        }
        for r in rows
    ]


# ── 장바구니 (DB 기반) ──


@router.get("/cart/items")
async def get_cart_items(limit: int = Query(200, ge=1, le=500)):
    db = await get_read_db()
    return await _get_cart_items(db, limit)


@router.post("/cart/add")
async def add_cart_item(request: Request):
    body = await request.json()
    sku_id = body.get("sku_id")
    if not sku_id:
        raise HTTPException(status_code=400, detail="sku_id is required")
    quantity = body.get("quantity", 1)
    if not isinstance(quantity, int) or quantity < 1 or quantity > 9999:
        raise HTTPException(status_code=400, detail="invalid quantity")
    db = await get_db()
    async with _write_lock:
        result = await _add_cart_item(
            db,
            sku_id=sku_id,
            barcode=body.get("barcode", ""),
            product_name=body.get("product_name", ""),
            quantity=quantity,
            added_by=body.get("added_by", "web"),
        )
    return {"status": "ok", **result}


@router.patch("/cart/{item_id}")
async def update_cart_item(item_id: int, request: Request):
    body = await request.json()
    quantity = body.get("quantity")
    if not isinstance(quantity, int) or quantity < 1 or quantity > 9999:
        raise HTTPException(status_code=400, detail="invalid quantity")
    db = await get_db()
    async with _write_lock:
        found = await _update_cart_item(db, item_id, quantity)
    if not found:
        raise HTTPException(status_code=404, detail="cart item not found")
    return {"status": "ok"}


@router.delete("/cart/all")
async def clear_cart_items():
    db = await get_db()
    async with _write_lock:
        count = await _clear_cart(db)
    return {"status": "ok", "deleted": count}


@router.delete("/cart/{item_id}")
async def delete_cart_item(item_id: int):
    db = await get_db()
    async with _write_lock:
        found = await _delete_cart_item(db, item_id)
    if not found:
        raise HTTPException(status_code=404, detail="cart item not found")
    return {"status": "ok"}


@router.post("/cart/export")
async def export_cart_to_gsheet():
    db = await get_read_db()
    items = await _get_cart_items(db, 500)
    if not items:
        return {"status": "ok", "message": "장바구니가 비어있습니다", "exported": 0}

    seen_skus: set[str] = set()
    exported = 0
    failed = 0
    last_error = ""
    for item in items:
        if item["sku_id"] in seen_skus:
            continue
        seen_skus.add(item["sku_id"])
        try:
            result = await asyncio.to_thread(
                _add_to_cart,
                item["barcode"],
                item["sku_id"],
                item["product_name"],
                item["quantity"],
                "web",
            )
            if result["status"] == "ok":
                exported += 1
            else:
                failed += 1
                last_error = result.get("message", "")
        except Exception as e:
            failed += 1
            last_error = str(e)

    if exported == 0 and failed > 0:
        raise HTTPException(status_code=500, detail=last_error or "구글시트 내보내기 실패")

    status = "ok" if failed == 0 else "partial"
    msg = f"{exported}건 내보내기 완료" + (f", {failed}건 실패" if failed else "")
    return {"status": status, "message": msg, "exported": exported, "failed": failed}


# ── 상품 등록 ──


@router.post("/product")
async def create_product(request: Request):
    body = await request.json()
    sku_id = body.get("sku_id", "").strip()
    if not sku_id:
        raise HTTPException(status_code=400, detail="sku_id is required")
    product_name = body.get("product_name", "").strip()
    if not product_name:
        raise HTTPException(status_code=400, detail="product_name is required")

    db = await get_db()
    async with _write_lock:
        existing = await (
            await db.execute("SELECT 1 FROM product WHERE sku_id = ?", (sku_id,))
        ).fetchone()
        if existing:
            raise HTTPException(status_code=409, detail="이미 존재하는 SKU")
        await db.execute(
            "INSERT INTO product (sku_id, product_name, category, brand) VALUES (?, ?, ?, ?)",
            (sku_id, product_name, body.get("category", ""), body.get("brand", "")),
        )
        await db.commit()
    return {"status": "ok", "sku_id": sku_id}


# ── 바코드 매핑 ──


@router.post("/product/{sku_id}/barcode")
async def add_barcode(sku_id: str, request: Request):
    body = await request.json()
    barcode = body.get("barcode", "").strip()
    if not barcode:
        raise HTTPException(status_code=400, detail="barcode is required")

    db = await get_db()
    async with _write_lock:
        existing = await (
            await db.execute("SELECT 1 FROM product WHERE sku_id = ?", (sku_id,))
        ).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="product not found")
        dup = await (
            await db.execute("SELECT 1 FROM barcode WHERE barcode = ?", (barcode,))
        ).fetchone()
        if dup:
            raise HTTPException(status_code=409, detail="이미 등록된 바코드")
        await db.execute("INSERT INTO barcode (barcode, sku_id) VALUES (?, ?)", (barcode, sku_id))
        await db.commit()
    return {"status": "ok", "barcode": barcode, "sku_id": sku_id}


@router.delete("/product/{sku_id}/barcode/{barcode}")
async def remove_barcode(sku_id: str, barcode: str):
    db = await get_db()
    async with _write_lock:
        result = await db.execute(
            "DELETE FROM barcode WHERE barcode = ? AND sku_id = ?", (barcode, sku_id)
        )
        await db.commit()
    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="barcode not found")
    return {"status": "ok"}


# ── 이미지 관리 ──


@router.post("/product/{sku_id}/image")
async def upload_product_image(sku_id: str, request: Request):
    from app.config import settings as _settings

    content_type = request.headers.get("content-type", "")
    if "multipart/form-data" not in content_type:
        raise HTTPException(status_code=400, detail="multipart/form-data required")

    form = await request.form()
    file = form.get("file")
    if not file:
        raise HTTPException(status_code=400, detail="file is required")

    db = await get_db()
    async with _write_lock:
        existing = await (
            await db.execute("SELECT 1 FROM product WHERE sku_id = ?", (sku_id,))
        ).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="product not found")

        barcodes = await (
            await db.execute("SELECT barcode FROM barcode WHERE sku_id = ?", (sku_id,))
        ).fetchall()
        barcode = barcodes[0][0] if barcodes else sku_id

        file_bytes = await file.read()
        ext = Path(file.filename).suffix or ".jpg"
        filename = f"{sku_id}_{int(time.time())}{ext}"
        rel_path = f"img/{filename}"

        if _settings.webdav_base_url:
            http_client = request.app.state.http_client
            webdav_url = f"{_settings.webdav_base_url}/{_settings.webdav_path_prefix}/{rel_path}"
            resp = await http_client.put(
                webdav_url,
                content=file_bytes,
                auth=(_settings.webdav_username, _settings.webdav_password),
            )
            if resp.status_code not in (200, 201, 204):
                raise HTTPException(status_code=500, detail=f"NAS 업로드 실패: {resp.status_code}")
        else:
            local_path = Path(_settings.image_cache_dir) / rel_path
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(file_bytes)

        await db.execute(
            "INSERT INTO image (barcode, file_path, image_type) VALUES (?, ?, 'full')",
            (barcode, rel_path),
        )
        await db.commit()

    return {"status": "ok", "file_path": rel_path}


@router.delete("/product/{sku_id}/image/{image_id}")
async def delete_product_image(sku_id: str, image_id: int, request: Request):
    from app.config import settings as _settings

    db = await get_db()
    async with _write_lock:
        sku_barcodes = await (
            await db.execute("SELECT barcode FROM barcode WHERE sku_id = ?", (sku_id,))
        ).fetchall()
        allowed = {r[0] for r in sku_barcodes} | {sku_id}
        img = await (
            await db.execute("SELECT barcode, file_path FROM image WHERE id = ?", (image_id,))
        ).fetchone()
        if not img or img[0] not in allowed:
            raise HTTPException(status_code=404, detail="image not found")

        file_path = img[1]
        await db.execute("DELETE FROM image WHERE id = ?", (image_id,))
        await db.commit()

    if file_path:
        if _settings.webdav_base_url:
            try:
                http_client = request.app.state.http_client
                webdav_url = (
                    f"{_settings.webdav_base_url}/{_settings.webdav_path_prefix}/{file_path}"
                )
                resp = await http_client.delete(
                    webdav_url,
                    auth=(_settings.webdav_username, _settings.webdav_password),
                )
                if resp.status_code >= 400 and resp.status_code != 404:
                    logger.warning("NAS 이미지 삭제 실패: %s → %d", file_path, resp.status_code)
            except Exception as e:
                logger.warning("NAS 이미지 삭제 오류: %s — %s", file_path, e)

        cache_base = Path(_settings.image_cache_dir)
        (cache_base / file_path).unlink(missing_ok=True)
        resized_dir = cache_base / "resized"
        if resized_dir.exists():
            for width_dir in resized_dir.iterdir():
                (width_dir / file_path).unlink(missing_ok=True)

    return {"status": "ok"}


# ── xlsx 일괄 업로드 (상품 + 바코드) ──


@router.post("/import/products")
async def import_products(request: Request):
    content_type = request.headers.get("content-type", "")
    if "multipart/form-data" not in content_type:
        raise HTTPException(status_code=400, detail="multipart/form-data required")

    form = await request.form()
    file = form.get("file")
    if not file:
        raise HTTPException(status_code=400, detail="file is required")

    from io import BytesIO

    import openpyxl

    file_bytes = await file.read()
    wb = openpyxl.load_workbook(BytesIO(file_bytes), read_only=True)
    try:
        ws = wb.active
        added = 0
        updated = 0
        skipped = 0

        db = await get_db()
        async with _write_lock:
            for row in ws.iter_rows(min_row=2, values_only=True):
                if not row or len(row) < 2:
                    continue
                sku_id = str(row[0]).strip() if row[0] else None
                product_name = str(row[1]).strip() if row[1] else None
                if not sku_id or not product_name:
                    skipped += 1
                    continue

                category = str(row[2]).strip() if len(row) > 2 and row[2] else ""
                brand = str(row[3]).strip() if len(row) > 3 and row[3] else ""
                barcode = str(row[4]).strip() if len(row) > 4 and row[4] else ""

                existing = await (
                    await db.execute("SELECT 1 FROM product WHERE sku_id = ?", (sku_id,))
                ).fetchone()
                if existing:
                    await db.execute(
                        "UPDATE product SET product_name=?, category=?, brand=?, updated_at=datetime('now') WHERE sku_id=?",
                        (product_name, category, brand, sku_id),
                    )
                    updated += 1
                else:
                    await db.execute(
                        "INSERT INTO product (sku_id, product_name, category, brand) VALUES (?, ?, ?, ?)",
                        (sku_id, product_name, category, brand),
                    )
                    added += 1

                if barcode:
                    dup = await (
                        await db.execute("SELECT 1 FROM barcode WHERE barcode = ?", (barcode,))
                    ).fetchone()
                    if not dup:
                        await db.execute(
                            "INSERT INTO barcode (barcode, sku_id) VALUES (?, ?)", (barcode, sku_id)
                        )

            await db.commit()
    finally:
        wb.close()

    return {
        "status": "ok",
        "message": f"{added}건 추가, {updated}건 수정, {skipped}건 스킵",
        "added": added,
        "updated": updated,
        "skipped": skipped,
    }


# ── 설정 / 진단 ──


@router.get("/settings")
async def get_settings():
    from app.config import settings as _settings

    return {
        "printer": {
            "name": _settings.printer_name or "(자동 감지)",
            "agent_url": _settings.print_agent_url or "(미설정)",
            "label_width_mm": _settings.label_width_mm,
            "label_height_mm": _settings.label_height_mm,
            "label_gap_mm": _settings.label_gap_mm,
            "label_density": _settings.label_density,
        },
        "nas": {
            "webdav_url": _settings.webdav_base_url or "(미설정)",
            "path_prefix": _settings.webdav_path_prefix,
            "connected": bool(_settings.webdav_base_url),
        },
        "gsheet": {
            "url": _settings.gsheet_url or "(미설정)",
            "configured": bool(_settings.gsheet_url),
        },
    }


@router.post("/nas/test")
async def test_nas_connection(request: Request):
    from app.config import settings as _settings

    if not _settings.webdav_base_url:
        return {"status": "error", "message": "WebDAV URL 미설정"}

    try:
        http_client = request.app.state.http_client
        resp = await http_client.request(
            "PROPFIND",
            f"{_settings.webdav_base_url}/{_settings.webdav_path_prefix}/",
            auth=(_settings.webdav_username, _settings.webdav_password),
            headers={"Depth": "0"},
        )
        if resp.status_code < 300:
            return {"status": "ok", "message": "NAS 연결 성공"}
        return {"status": "error", "message": f"NAS 응답: {resp.status_code}"}
    except Exception as e:
        return {"status": "error", "message": str(e)}


@router.post("/print/test")
async def test_print(request: Request):
    from app.config import settings as _settings

    if _settings.print_agent_url:
        try:
            http_client = request.app.state.http_client
            resp = await http_client.get(
                _settings.print_agent_url.replace("/print", "/health"), timeout=5.0
            )
            if resp.status_code == 200:
                return {"status": "ok", "message": "인쇄 에이전트 연결 성공"}
            return {"status": "error", "message": f"에이전트 응답: {resp.status_code}"}
        except Exception as e:
            return {"status": "error", "message": str(e)}
    return {"status": "error", "message": "print_agent_url 미설정"}


_MASTER_PHOTO_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "static", "photos")
_MASTER_ALLOWED_EXT = {".jpg", ".jpeg", ".png", ".webp"}
_MASTER_MAX_SIZE = 10 * 1024 * 1024


@router.post("/product-master/{master_id}/image")
async def upload_product_master_image(
    master_id: int,
    file: UploadFile,
    image_type: str = Form(...),
):
    if image_type not in ("option", "sourcing"):
        raise HTTPException(400, "image_type must be 'option' or 'sourcing'")

    content = await file.read()
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in _MASTER_ALLOWED_EXT:
        raise HTTPException(400, f"unsupported file type: {ext}")
    if len(content) > _MASTER_MAX_SIZE:
        raise HTTPException(400, "file too large (max 10MB)")
    try:
        Image.open(io.BytesIO(content)).verify()
    except Exception:
        raise HTTPException(400, "invalid image file")

    db = await get_db()
    cursor = await db.execute(
        "SELECT 1 FROM product_master WHERE id = ?", (master_id,)
    )
    if not await cursor.fetchone():
        raise HTTPException(404, "product_master not found")

    img = Image.open(io.BytesIO(content))
    if img.mode in ("RGBA", "P"):
        img = img.convert("RGB")
    max_dim = 1920
    if max(img.size) > max_dim:
        img.thumbnail((max_dim, max_dim), Image.LANCZOS)

    os.makedirs(_MASTER_PHOTO_DIR, exist_ok=True)
    suffix = uuid.uuid4().hex[:8]
    filename = f"master_{master_id}_{image_type}_{suffix}.jpg"
    filepath = os.path.join(_MASTER_PHOTO_DIR, filename)
    img.save(filepath, "JPEG", quality=85)

    rel_path = f"/static/photos/{filename}"
    async with _write_lock:
        cursor = await db.execute(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM product_master_image "
            "WHERE product_master_id = ? AND image_type = ?",
            (master_id, image_type),
        )
        next_order = (await cursor.fetchone())[0]
        cursor = await db.execute(
            "INSERT INTO product_master_image (product_master_id, file_path, image_type, sort_order) "
            "VALUES (?, ?, ?, ?)",
            (master_id, rel_path, image_type, next_order),
        )
        image_id = cursor.lastrowid
        await db.commit()

    return {"id": image_id, "file_path": rel_path, "image_type": image_type}


@router.delete("/product-master/{master_id}/image/{image_id}")
async def delete_product_master_image(master_id: int, image_id: int):
    db = await get_db()
    async with _write_lock:
        cursor = await db.execute(
            "SELECT file_path FROM product_master_image WHERE id = ? AND product_master_id = ?",
            (image_id, master_id),
        )
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(404, "image not found")

        rel_path = row["file_path"]
        await db.execute("DELETE FROM product_master_image WHERE id = ?", (image_id,))
        await db.commit()

    file_path = os.path.join(os.path.dirname(__file__), "..", "..", rel_path.lstrip("/"))
    await asyncio.to_thread(lambda: os.path.exists(file_path) and os.remove(file_path))

    return {"status": "deleted"}
