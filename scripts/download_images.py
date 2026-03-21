#!/usr/bin/env python3
"""쿠팡 CDN ACTIVE 상품 이미지 일괄 다운로드"""

import asyncio
import json
import sys
from pathlib import Path
from dataclasses import dataclass

import aiohttp
from tqdm.asyncio import tqdm

# ── 설정 ────────────────────────────────────────────────────────────────────
JSON_PATH = Path(__file__).parent.parent / "supplier_hub_skus_2026-03-21.json"
OUTPUT_DIR = Path("/Volumes/NAS/coupang_images")   # NAS: /Volumes/NAS/..., 로컬: Path("images")
CONCURRENCY = 20
MAX_RETRIES = 3
TIMEOUT_SEC = 30
# ─────────────────────────────────────────────────────────────────────────────


@dataclass
class Sku:
    sku_id: int
    barcode: str
    image_url: str


def load_active_skus(json_path: Path) -> list[Sku]:
    with open(json_path, encoding="utf-8") as f:
        data: list[dict] = json.load(f)
    return [
        Sku(
            sku_id=item["skuId"],
            barcode=item["barcode"],
            image_url=item["imagePath"],
        )
        for item in data
        if item.get("orderStatus") == "ACTIVE" and item.get("imagePath")
    ]


def dest_path(output_dir: Path, sku: Sku) -> Path:
    return output_dir / f"{sku.sku_id}_{sku.barcode}.jpg"


async def download_one(
    session: aiohttp.ClientSession,
    sku: Sku,
    output_dir: Path,
    semaphore: asyncio.Semaphore,
) -> tuple[int, bool, str | None]:
    """(sku_id, success, error_msg) 반환"""
    path = dest_path(output_dir, sku)
    if path.exists():
        return sku.sku_id, True, None

    for attempt in range(1, MAX_RETRIES + 1):
        async with semaphore:
            try:
                async with session.get(
                    sku.image_url,
                    timeout=aiohttp.ClientTimeout(total=TIMEOUT_SEC),
                ) as resp:
                    if resp.status != 200:
                        raise aiohttp.ClientResponseError(
                            resp.request_info, resp.history, status=resp.status
                        )
                    # TODO: 필요 시 Content-Type 검증 추가
                    data = await resp.read()
                    path.write_bytes(data)
                    return sku.sku_id, True, None
            except Exception as e:
                if attempt == MAX_RETRIES:
                    return sku.sku_id, False, str(e)
                await asyncio.sleep(2 ** (attempt - 1))

    return sku.sku_id, False, "unreachable"


async def run(skus: list[Sku], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(CONCURRENCY)
    failed: list[tuple[int, str]] = []

    connector = aiohttp.TCPConnector(limit=CONCURRENCY)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [download_one(session, sku, output_dir, semaphore) for sku in skus]
        for coro in tqdm.as_completed(tasks, total=len(tasks), desc="downloading"):
            sku_id, success, err = await coro
            if not success:
                failed.append((sku_id, err or "unknown"))

    print(f"\n완료: {len(skus) - len(failed)}/{len(skus)}")
    if failed:
        print(f"실패 {len(failed)}건:")
        for sku_id, err in failed[:20]:
            print(f"  skuId={sku_id}: {err}")
        # TODO: 실패 목록을 failed_skus.json으로 저장해 재시도 가능하게
        failed_log = output_dir / "failed_skus.json"
        failed_log.write_text(
            json.dumps([{"skuId": s, "error": e} for s, e in failed], ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"실패 목록 저장: {failed_log}")


def main() -> None:
    skus = load_active_skus(JSON_PATH)
    print(f"ACTIVE SKU: {len(skus)}개 → {OUTPUT_DIR}")

    # TODO: argparse로 --output-dir, --concurrency, --dry-run 옵션 추가 가능
    asyncio.run(run(skus, OUTPUT_DIR))


if __name__ == "__main__":
    # 의존성: pip install aiohttp tqdm
    main()
