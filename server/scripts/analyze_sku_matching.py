#!/usr/bin/env python3
"""
SKU 매칭 커버리지 분석 스크립트
대상: .context/sku_with_category.csv
"""

import csv
import collections
import re
import sys

CSV_PATH = "/Users/leeeunseok/Projects/scan/.context/sku_with_category.csv"

if len(sys.argv) > 1:
    CSV_PATH = sys.argv[1]


def is_empty_qid(v):
    return not v or v.strip() == "" or v.strip().lower() == "null"


def extract_leaf_category(path):
    if not path:
        return "(없음)"
    last = path.split(">")[-1].strip()
    last = re.sub(r"\s*\(\d+\)\s*$", "", last).strip()
    return last if last else "(없음)"


# CSV 로드
rows = []
with open(CSV_PATH, encoding="utf-8-sig", newline="") as f:
    reader = csv.DictReader(f)
    for row in reader:
        rows.append(row)

total_sku = len(rows)
print(f"총 SKU 수: {total_sku:,}")

# 1. 기본 통계
print("\n[1] 기본 통계")
state_counts = collections.Counter(r["state"] for r in rows)
for k, v in state_counts.most_common():
    print(f"  {k}: {v:,} ({v/total_sku*100:.1f}%)")

reject_status_counts = collections.Counter(r["rejectStatus"] if r["rejectStatus"] else "(빈값)" for r in rows)
print("  rejectStatus:")
for k, v in reject_status_counts.most_common():
    print(f"    {k}: {v:,} ({v/total_sku*100:.1f}%)")

# 2. quotationId 분석
print("\n[2] quotationId 분석")
valid_qid = [r for r in rows if not is_empty_qid(r["quotationId"])]
qid_groups = collections.defaultdict(list)
for r in valid_qid:
    qid_groups[r["quotationId"].strip()].append(r)

group_sizes = [len(v) for v in qid_groups.values()]
sorted_sizes = sorted(group_sizes)
n = len(sorted_sizes)

print(f"  유니크 quotationId: {len(qid_groups):,}")
print(f"  빈/null SKU: {total_sku - len(valid_qid):,} ({(total_sku - len(valid_qid))/total_sku*100:.1f}%)")
print(f"  그룹 크기 분포:")
for label, cond in [("1개", lambda s: s==1), ("2~5개", lambda s: 2<=s<=5),
                     ("6~10개", lambda s: 6<=s<=10), ("11~20개", lambda s: 11<=s<=20),
                     ("21+개", lambda s: s>=21)]:
    cnt = sum(1 for s in group_sizes if cond(s))
    print(f"    {label}: {cnt:,}그룹")
print(f"  평균: {sum(group_sizes)/n:.2f}, 중앙값: {sorted_sizes[n//2]}, 최댓값: {max(group_sizes)}")

# 3. 자동 매칭 커버리지
print("\n[3] 자동 매칭 커버리지")
multi_sku_groups = {qid: skus for qid, skus in qid_groups.items() if len(skus) >= 2}
multi_sku_count = sum(len(v) for v in multi_sku_groups.values())
single_sku_count = total_sku - multi_sku_count
coverage_pct = multi_sku_count / total_sku * 100
print(f"  2+ 그룹 수: {len(multi_sku_groups):,}")
print(f"  해당 SKU 수: {multi_sku_count:,}")
print(f"  ★ 자동 매칭 커버리지: {coverage_pct:.1f}%")
print(f"  ★ 70% 달성: {'YES' if coverage_pct >= 70 else 'NO'}")
print(f"  product_master 후보: {len(multi_sku_groups):,}개")
print(f"  상품당 평균 SKU: {multi_sku_count/len(multi_sku_groups):.2f}")
print(f"  2차 매칭 필요 SKU: {single_sku_count:,} ({single_sku_count/total_sku*100:.1f}%)")

# 4. 카테고리 분포 (상위 20개)
print("\n[4] 카테고리 분포 (상위 20개)")
cat_counts = collections.Counter(extract_leaf_category(r["internalCategoryPath"]) for r in rows)
for cat, cnt in cat_counts.most_common(20):
    print(f"  {cat:<35} {cnt:>6,} ({cnt/total_sku*100:.1f}%)")

# 5. 상위 5 그룹 샘플 (제목만)
print("\n[5] SKU 수 상위 5개 quotationId 그룹")
top5 = sorted(qid_groups.items(), key=lambda x: len(x[1]), reverse=True)[:5]
for i, (qid, skus) in enumerate(top5, 1):
    names = sorted(set(r["productName"] for r in skus))
    print(f"  #{i}: {qid} ({len(skus)}개 SKU, {len(names)}개 유니크 상품명)")
    for name in names[:3]:
        print(f"    - {name[:80]}")
    if len(names) > 3:
        print(f"    ... 외 {len(names)-3}개")

# 6. 2차 매칭 추정
print("\n[6] 2차 매칭 후보 추정 (단일 그룹 대상)")
single_skus = [r for qid, skus in qid_groups.items() if len(skus) == 1 for r in skus]
prefix_groups = collections.defaultdict(list)
for r in single_skus:
    prefix = r["productName"][:20].strip() if r["productName"] else "(없음)"
    prefix_groups[prefix].append(r)
multi_prefix = {k: v for k, v in prefix_groups.items() if len(v) >= 2}
multi_prefix_count = sum(len(v) for v in multi_prefix.values())
print(f"  총 그룹 수: {len(prefix_groups):,}")
print(f"  2+ 그룹 수: {len(multi_prefix):,}")
print(f"  2차 매칭 상한 SKU: {multi_prefix_count:,} ({multi_prefix_count/len(single_skus)*100:.1f}%)")

# 7. REJECTION 분석
print("\n[7] REJECTION 분석")
rejection_skus = [r for r in rows if r["state"] == "REJECTION"]
rejection_count = len(rejection_skus)
print(f"  REJECTION: {rejection_count:,} ({rejection_count/total_sku*100:.1f}%)")
rej_cat = collections.Counter(extract_leaf_category(r["internalCategoryPath"]) for r in rejection_skus)
print("  상위 5개 카테고리:")
for cat, cnt in rej_cat.most_common(5):
    print(f"    {cat:<35} {cnt:>5,} ({cnt/rejection_count*100:.1f}%)")
rej_in_multi = sum(1 for r in rejection_skus if r["quotationId"].strip() in multi_sku_groups)
print(f"  REJECTION 중 2+ 그룹 속한 SKU: {rej_in_multi:,} ({rej_in_multi/rejection_count*100:.1f}%)")
