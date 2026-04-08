#!/usr/bin/env python3
"""
하이브리드 2단 분할 시뮬레이션
1단계: 카테고리(최하위) 기반 분할
2단계: 유형 키워드 → 브랜드 키워드 기반 분할
"""

import argparse
import collections
import csv
import re
import sys
from datetime import datetime

# ── 상수 ──────────────────────────────────────────────────────────────────────

CSV_PATH = "/Users/leeeunseok/Projects/scan/.context/sku_with_category.csv"

TYPE_KEYWORDS = [
    "케이스",
    "필름",
    "밴드",
    "스트랩",
    "베젤링",
    "베젤",
    "스티커",
    "펜촉",
    "터치펜",
    "충전기",
    "케이블",
    "도킹",
    "교체용캡",
    "교체팁",
    "보호필름",
    "강화유리",
    "방수팩",
    "마운트",
    "핸드그립",
    "삼각대",
    "렌즈",
    "키링",
    "스킨",
    "파우치",
    "보관함",
    "거치대",
    "크래들",
    "스테이션",
    "핸들",
    "그립",
    "필터",
    "후드",
    "후크",
    "어댑터",
    # 2026-04-08 사전 보강 (미매칭 상품명 분석 결과)
    "스틱캡",
    "스틱커버",
    "이어패드",
    "이어팁",
    "헤드셋",
    "트리거",
    "장갑",
    "물걸레",
    "브러시",
    "텀블러",
    "드링크백",
    "젠더",
    "라켓",
    "머쉬룸",
    # 2차 보강
    "목걸이",
    "지갑",
    "슬리브",
    "컨트롤러",
    "모듈",
    "센서",
]

BRAND_KEYWORDS = [
    # 2026-04-08 사전 보강 — 구체적 기종부터 우선 매칭
    "ROMO",
    "오즈모",
    "조이콘",
    "프로콘",
    "플라이디지",
    "보이스캐디",
    # 기존
    "갤럭시",
    "아이폰",
    "애플",
    "삼성",
    "샤오미",
    "미밴드",
    "레드미",
    "화웨이",
    "아마존핏",
    "어메이즈핏",
    "워치",
    "핏",
    "파워비츠",
    "비츠",
    "에어팟",
    "닌텐도",
    "스위치",
    "DJI",
    "인스타360",
    "고프로",
    "쵸미앤세븐",
    "플스",
    "피에스",
    "아이패드",
    "탭",
    "S펜",
    "갈럭시",
    "Z플립",
    "Z폴드",
]

# ── 헬퍼 ──────────────────────────────────────────────────────────────────────


def extract_leaf_category(path):
    if not path or path.strip() == "":
        return "(없음)"
    last = path.split(">")[-1].strip()
    last = re.sub(r"\s*\(\d+\)\s*$", "", last).strip()
    return last if last else "(없음)"


def find_type_keyword(name):
    # 공백 무시 매칭 ("스틱 캡" ↔ "스틱캡" 등)
    name_ns = name.replace(" ", "")
    for kw in TYPE_KEYWORDS:
        if kw.replace(" ", "") in name_ns:
            return kw
    return "기타유형"


def find_brand_keyword(name):
    name_ns = name.replace(" ", "")
    for kw in BRAND_KEYWORDS:
        if kw.replace(" ", "") in name_ns:
            return kw
    return "기타브랜드"


def homogeneity_score(skus):
    """동질성 점수: 1 - (고유 앞20자 prefix 수 / SKU 수)"""
    n = len(skus)
    if n == 0:
        return 1.0
    prefixes = set(r["productName"][:20].strip() if r["productName"] else "" for r in skus)
    return 1.0 - (len(prefixes) / n)


# ── 데이터 로드 ────────────────────────────────────────────────────────────────


def load_data(path):
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


# ── 기준선: quotationId 기반 그룹화 ────────────────────────────────────────────


def baseline_groups(rows):
    groups = collections.defaultdict(list)
    for r in rows:
        qid = r["quotationId"].strip()
        groups[qid].append(r)
    return dict(groups)


# ── 1단계: 카테고리 분할 ──────────────────────────────────────────────────────


def stage1_split(qid_groups):
    """
    같은 quotationId 내에서 최하위 카테고리가 다르면 분리.
    카테고리 없음("(없음)")인 SKU는 같은 그룹으로 유지.
    새 그룹 키: (quotationId, leaf_category)
    """
    new_groups = {}
    for qid, skus in qid_groups.items():
        cat_buckets = collections.defaultdict(list)
        for r in skus:
            cat = extract_leaf_category(r.get("internalCategoryPath", ""))
            cat_buckets[cat].append(r)

        # "(없음)" 버킷: 카테고리 있는 그룹들에 병합 or 단독 유지
        # 규칙: "(없음)"은 별도로 "(없음)" 키로 유지
        for cat, bucket in cat_buckets.items():
            key = f"{qid}||CAT:{cat}"
            new_groups[key] = bucket
    return new_groups


# ── 2단계: 유형 + 브랜드 키워드 분할 ─────────────────────────────────────────


def stage2_split(stage1_groups):
    """
    1단계 결과 각 그룹 내에서 유형 키워드로 분할.
    유형 분할 후에도 브랜드가 다르면 추가 분할.
    """
    new_groups = {}
    for key, skus in stage1_groups.items():
        # 유형별 분류
        type_buckets = collections.defaultdict(list)
        for r in skus:
            t = find_type_keyword(r["productName"])
            type_buckets[t].append(r)

        if len(type_buckets) <= 1:
            # 유형이 하나 → 브랜드 분할 시도
            type_label = list(type_buckets.keys())[0]
            brand_buckets = collections.defaultdict(list)
            for r in skus:
                b = find_brand_keyword(r["productName"])
                brand_buckets[b].append(r)
            if len(brand_buckets) <= 1:
                new_groups[f"{key}||TYPE:{type_label}"] = skus
            else:
                for brand, bucket in brand_buckets.items():
                    new_groups[f"{key}||TYPE:{type_label}||BRAND:{brand}"] = bucket
        else:
            # 유형이 2개 이상 → 유형별 분할, 각 유형 내에서 브랜드 추가 분할
            for type_label, type_skus in type_buckets.items():
                brand_buckets = collections.defaultdict(list)
                for r in type_skus:
                    b = find_brand_keyword(r["productName"])
                    brand_buckets[b].append(r)
                if len(brand_buckets) <= 1:
                    new_groups[f"{key}||TYPE:{type_label}"] = type_skus
                else:
                    for brand, bucket in brand_buckets.items():
                        new_groups[f"{key}||TYPE:{type_label}||BRAND:{brand}"] = bucket
    return new_groups


# ── 통계 계산 ─────────────────────────────────────────────────────────────────


def compute_stats(groups, total_sku):
    sizes = [len(v) for v in groups.values()]
    multi = {k: v for k, v in groups.items() if len(v) >= 2}
    single_count = sum(1 for s in sizes if s == 1)
    multi_sku_count = sum(len(v) for v in multi.values())
    n = len(sizes)
    sorted_sizes = sorted(sizes)
    avg = sum(sizes) / n if n else 0
    median = sorted_sizes[n // 2] if n else 0
    max_size = max(sizes) if sizes else 0
    coverage = multi_sku_count / total_sku * 100

    homo_scores = [homogeneity_score(v) for v in groups.values() if len(v) >= 2]
    avg_homo = sum(homo_scores) / len(homo_scores) if homo_scores else 0
    median_homo = sorted(homo_scores)[len(homo_scores) // 2] if homo_scores else 0

    homo_dist = {
        "1.0": sum(1 for s in homo_scores if s == 1.0),
        "0.8~1.0": sum(1 for s in homo_scores if 0.8 <= s < 1.0),
        "0.5~0.8": sum(1 for s in homo_scores if 0.5 <= s < 0.8),
        "0.0~0.5": sum(1 for s in homo_scores if s < 0.5),
    }

    return {
        "pm_count": n,
        "avg_sku": round(avg, 2),
        "median_sku": median,
        "max_size": max_size,
        "multi_sku_count": multi_sku_count,
        "single_count": single_count,
        "coverage": round(coverage, 1),
        "avg_homo": round(avg_homo, 4),
        "median_homo": round(median_homo, 4),
        "homo_dist": homo_dist,
    }


# ── 케이스 검증 ───────────────────────────────────────────────────────────────

TARGET_QIDS_RANK = [1, 2, 4]  # 상위 1, 2, 4번 그룹


def get_top_qids(base_groups, ranks):
    sorted_groups = sorted(base_groups.items(), key=lambda x: len(x[1]), reverse=True)
    result = {}
    for rank in ranks:
        if rank - 1 < len(sorted_groups):
            qid, skus = sorted_groups[rank - 1]
            result[rank] = (qid, skus)
    return result


def case_verification(target_qids, stage1_groups, stage2_groups):
    results = {}
    for rank, (qid, orig_skus) in target_qids.items():
        # 1단계: 이 qid에 해당하는 그룹들
        s1_matching = {k: v for k, v in stage1_groups.items() if k.startswith(f"{qid}||CAT:")}
        s2_matching = {k: v for k, v in stage2_groups.items() if k.startswith(f"{qid}||CAT:")}

        results[rank] = {
            "qid": qid,
            "orig_size": len(orig_skus),
            "s1_groups": s1_matching,
            "s2_groups": s2_matching,
        }
    return results


# ── 혼재 그룹 찾기 ────────────────────────────────────────────────────────────


def find_mixed_groups(stage2_groups):
    """2단계 후에도 여러 유형이 섞인 그룹 찾기"""
    mixed = []
    for key, skus in stage2_groups.items():
        if len(skus) < 2:
            continue
        types_in_group = set(find_type_keyword(r["productName"]) for r in skus)
        if len(types_in_group) > 1:
            mixed.append((key, skus, types_in_group))
    mixed.sort(key=lambda x: len(x[1]), reverse=True)
    return mixed


# ── 기타유형/기타브랜드 분석 ──────────────────────────────────────────────────


def analyze_unmatched(rows, stage2_groups):
    # 기타유형 SKU
    other_type = [r for r in rows if find_type_keyword(r["productName"]) == "기타유형"]
    other_brand = [r for r in rows if find_brand_keyword(r["productName"]) == "기타브랜드"]

    # 상위 10개 기타유형 상품명
    other_type_names = collections.Counter(r["productName"][:60] for r in other_type)

    return {
        "other_type_count": len(other_type),
        "other_type_pct": len(other_type) / len(rows) * 100,
        "other_brand_count": len(other_brand),
        "other_type_top10": other_type_names.most_common(10),
    }


# ── 메인 ──────────────────────────────────────────────────────────────────────


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=None, help="마크다운 리포트 출력 경로")
    args = parser.parse_args()

    rows = load_data(CSV_PATH)
    total_sku = len(rows)

    base = baseline_groups(rows)
    s1 = stage1_split(base)
    s2 = stage2_split(s1)

    base_stats = compute_stats(base, total_sku)
    s1_stats = compute_stats(s1, total_sku)
    s2_stats = compute_stats(s2, total_sku)

    target_qids = get_top_qids(base, TARGET_QIDS_RANK)
    case_results = case_verification(target_qids, s1, s2)
    mixed_groups = find_mixed_groups(s2)
    unmatched = analyze_unmatched(rows, s2)

    # ── 출력 ─────────────────────────────────────────────────────────────────

    lines = []
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    lines.append("# 하이브리드 2단 분할 시뮬레이션 결과\n")
    lines.append(f"**분석 일시:** {ts}  \n**총 SKU:** {total_sku:,}개\n")
    lines.append("---\n")

    # 분석 1: 전후 비교 요약표
    lines.append("## 1. 전후 비교 요약표\n")
    lines.append("| 항목 | 적용 전 (quotationId만) | 1단계 후 (카테고리) | 2단계 후 (키워드까지) |")
    lines.append("|------|------|------|------|")
    rows_table = [
        ("product_master 수", base_stats["pm_count"], s1_stats["pm_count"], s2_stats["pm_count"]),
        (
            "평균 SKU/product_master",
            base_stats["avg_sku"],
            s1_stats["avg_sku"],
            s2_stats["avg_sku"],
        ),
        ("중앙값 SKU", base_stats["median_sku"], s1_stats["median_sku"], s2_stats["median_sku"]),
        ("최대 그룹 크기", base_stats["max_size"], s1_stats["max_size"], s2_stats["max_size"]),
        (
            "2+ SKU 그룹에 속한 SKU 수",
            base_stats["multi_sku_count"],
            s1_stats["multi_sku_count"],
            s2_stats["multi_sku_count"],
        ),
        (
            "단일 SKU 그룹 수",
            base_stats["single_count"],
            s1_stats["single_count"],
            s2_stats["single_count"],
        ),
        (
            "자동 매칭 커버리지 %",
            f"{base_stats['coverage']}%",
            f"{s1_stats['coverage']}%",
            f"{s2_stats['coverage']}%",
        ),
    ]
    for label, b, s1v, s2v in rows_table:
        lines.append(f"| {label} | {b} | {s1v} | {s2v} |")
    lines.append("")

    # 분석 2: 동질성 점수
    lines.append("## 2. 동질성 점수 분포\n")
    lines.append("| 구간 | 적용 전 그룹 수 | 1단계 후 | 2단계 후 |")
    lines.append("|------|----------------|----------|----------|")
    for band in ["1.0", "0.8~1.0", "0.5~0.8", "0.0~0.5"]:
        b = base_stats["homo_dist"][band]
        s1v = s1_stats["homo_dist"][band]
        s2v = s2_stats["homo_dist"][band]
        diff = s2v - b
        sign = "+" if diff >= 0 else ""
        lines.append(f"| 동질성 {band} | {b:,} | {s1v:,} | {s2v:,} ({sign}{diff}) |")
    lines.append("")
    lines.append("| 지표 | 적용 전 | 1단계 후 | 2단계 후 |")
    lines.append("|------|---------|----------|----------|")
    lines.append(
        f"| 평균 동질성 | {base_stats['avg_homo']:.4f} | {s1_stats['avg_homo']:.4f} | {s2_stats['avg_homo']:.4f} |"
    )
    lines.append(
        f"| 중앙값 동질성 | {base_stats['median_homo']:.4f} | {s1_stats['median_homo']:.4f} | {s2_stats['median_homo']:.4f} |"
    )
    lines.append("")

    # 분석 3: 케이스 검증
    lines.append("## 3. 구체적 케이스 검증\n")
    for rank, info in case_results.items():
        qid = info["qid"]
        orig_size = info["orig_size"]
        s1g = info["s1_groups"]
        s2g = info["s2_groups"]

        lines.append(f"### 그룹 #{rank} 원본 ({orig_size}개): `{qid[:30]}...`\n")

        lines.append(f"**1단계 (카테고리 분할) 결과: {len(s1g)}개로 분할**\n")
        for k, v in sorted(s1g.items(), key=lambda x: len(x[1]), reverse=True):
            cat = k.split("||CAT:")[-1]
            samples = [r["productName"][:60] for r in v[:3]]
            lines.append(f"- [{cat}] {len(v)}개: " + " / ".join(f'"{s}"' for s in samples))
        lines.append("")

        lines.append(f"**2단계 (키워드 분할) 결과: {len(s2g)}개로 분할**\n")
        for k, v in sorted(s2g.items(), key=lambda x: len(x[1]), reverse=True):
            parts = k.split("||")
            type_part = next((p.replace("TYPE:", "") for p in parts if p.startswith("TYPE:")), "?")
            brand_part = next(
                (p.replace("BRAND:", "") for p in parts if p.startswith("BRAND:")), None
            )
            label = f"유형:{type_part}" + (f" / 브랜드:{brand_part}" if brand_part else "")
            lines.append(f"- [{label}] {len(v)}개")
        lines.append("")

    # 분석 4: 여전히 혼재된 그룹
    lines.append("## 4. 여전히 혼재된 그룹 (2단계 후)\n")
    lines.append(f"혼재 그룹 수: **{len(mixed_groups)}개**\n")
    lines.append("| # | 그룹 키 (요약) | SKU 수 | 혼재 유형 |")
    lines.append("|---|--------------|--------|---------|")
    for i, (key, skus, types) in enumerate(mixed_groups[:10], 1):
        short_key = key[:50] + "..." if len(key) > 50 else key
        lines.append(f"| {i} | `{short_key}` | {len(skus)} | {', '.join(sorted(types))} |")
    lines.append("")

    # 분석 5: 기타유형/기타브랜드
    lines.append("## 5. '기타유형' / '기타브랜드' 그룹 분석\n")
    lines.append(
        f"- 유형 키워드 매칭 실패 SKU: **{unmatched['other_type_count']:,}개** ({unmatched['other_type_pct']:.1f}%)"
    )
    lines.append(
        f"- 브랜드 키워드 매칭 실패 SKU: **{unmatched['other_brand_count']:,}개** ({unmatched['other_brand_count'] / total_sku * 100:.1f}%)\n"
    )
    lines.append("### 기타유형 상위 10개 상품명 샘플\n")
    lines.append("| 상품명 (앞 60자) | 건수 |")
    lines.append("|----------------|------|")
    for name, cnt in unmatched["other_type_top10"]:
        lines.append(f"| {name} | {cnt} |")
    lines.append("")

    # 분석 6: 최종 커버리지
    lines.append("## 6. 최종 커버리지 유지 확인\n")
    lines.append(f"- 2단계 후 2+ SKU 그룹에 속한 SKU: **{s2_stats['multi_sku_count']:,}개**")
    lines.append(f"- 전체 SKU: **{total_sku:,}개**")
    lines.append(f"- **최종 자동 매칭 커버리지: {s2_stats['coverage']}%**")
    coverage_ok = s2_stats["coverage"] >= 70
    lines.append(f"- 70% 달성 유지: **{'YES ✓' if coverage_ok else 'NO ✗'}**\n")

    # 분석 7: 실무 영향
    pm_before = sum(1 for v in base.values() if len(v) >= 2)
    pm_after = sum(1 for v in s2.values() if len(v) >= 2)
    pm_increase = pm_after - pm_before
    manual_review = len(mixed_groups)

    lines.append("## 7. 실무 영향 추정\n")
    lines.append("| 항목 | 값 |")
    lines.append("|------|-----|")
    lines.append(f"| product_master 수 (적용 전) | {pm_before:,}개 |")
    lines.append(f"| product_master 수 (2단계 후) | {pm_after:,}개 |")
    lines.append(f"| 외박스 QR 라벨 증가량 | +{pm_increase:,}개 |")
    lines.append(f"| 수동 검토 필요 SKU (혼재 그룹) | {manual_review}개 그룹 |")
    lines.append(f"| 기타유형 수동 분류 필요 SKU | {unmatched['other_type_count']:,}개 |")
    lines.append("")

    # 결론
    homo_improve = s2_stats["avg_homo"] - base_stats["avg_homo"]
    homo_sign = "+" if homo_improve >= 0 else ""
    lines.append("## 결론\n")
    lines.append(
        f"- **product_master: {pm_before:,}개 → {pm_after:,}개** (+{pm_increase:,}개, {pm_increase / pm_before * 100:.1f}% 증가)"
    )
    lines.append(
        f"- **최종 커버리지: {s2_stats['coverage']}%** ({'유지 ✓' if coverage_ok else '하락 경고 ✗'})"
    )
    lines.append(
        f"- **동질성 평균: {base_stats['avg_homo']:.4f} → {s2_stats['avg_homo']:.4f}** ({homo_sign}{homo_improve:.4f})"
    )
    lines.append(
        f"- **유형 키워드 미매칭: {unmatched['other_type_pct']:.1f}%** ({unmatched['other_type_count']:,}개)\n"
    )
    lines.append("### 권장 사항\n")

    if pm_increase > pm_before * 0.5:
        lines.append(
            "1. **키워드 사전 보강 우선**: 미매칭 상위 10개 상품명 확인 후 유형 키워드 추가 → 기타유형 비율 낮추기"
        )
    else:
        lines.append("1. **하이브리드 적용 권장**: product_master 분할이 품질 개선에 유효함")

    if len(mixed_groups) > 20:
        lines.append(
            f"2. **혼재 그룹 {len(mixed_groups)}개 수동 검토 필요**: 키워드 사전으로 해결 안 되는 이질 그룹, 별도 플래그 처리 고려"
        )
    else:
        lines.append(
            f"2. **혼재 그룹 {len(mixed_groups)}개**: 규모 작음, product_master 생성 후 검수 단계에서 처리 가능"
        )

    lines.append(
        f"3. **커버리지 {s2_stats['coverage']}% 유지**: 분할로 인한 단일화 최소, 실용성 확보됨"
    )

    report_text = "\n".join(lines)
    print(report_text)

    # 파일 저장
    if args.output:
        out_path = args.output
    else:
        ts_file = datetime.now().strftime("%Y%m%d-%H%M%S")
        out_path = f"/Users/leeeunseok/Projects/scan/.omc/research/matching-hybrid-{ts_file}.md"

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(report_text)
    print(f"\n---\nReport saved to: {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
