/**
 * 쿠팡 서플라이어 허브 SKU + 이미지 수집 스크립트
 *
 * 사용법:
 * 1. https://supplier.coupang.com/plan/ticket/supplySkuList 에 로그인
 * 2. 브라우저 DevTools (F12) → Console 탭
 * 3. 이 스크립트 전체를 붙여넣고 Enter
 * 4. 완료되면 JSON 파일이 자동 다운로드됨
 */

(async () => {
  const API_URL = "/plan/v1/ticket/sku/listTicketSku";
  const PAGE_SIZE = 100;
  const DELAY_MS = 300;
  const allItems = [];
  let page = 1;
  let totalSize = 0;

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  const fetchPage = async (pageNum) => {
    const res = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({
        skuId: "",
        skuName: "",
        barcode: "",
        orderingStatus: "",
        unit1: "",
        unit2: "",
        issueStatus: "",
        issueType: "",
        size: PAGE_SIZE,
        page: pageNum,
      }),
    });

    if (!res.ok) throw new Error(`HTTP ${res.status} at page ${pageNum}`);
    return res.json();
  };

  console.log("🔍 첫 페이지 조회 중...");
  const first = await fetchPage(1);
  totalSize = first.body?.total || first.total || 0;
  const totalPages = Math.ceil(totalSize / PAGE_SIZE);
  console.log(`📦 총 ${totalSize}건, ${totalPages}페이지`);

  const extractItems = (content) =>
    content.map((item) => ({
      skuId: item.skuId,
      skuName: item.skuName,
      barcode: item.barCode || item.barcode || "",
      imagePath: item.imagePath || "",
      orderStatus: item.orderStatus || item.orderingStatus || "",
      issueCount: item.issueCount || 0,
      mdId: item.mdId || "",
      mdName: item.mdName || "",
      scmId: item.scmId || "",
      scmName: item.scmName || "",
    }));

  const content1 = first.body?.content || first.content || [];
  allItems.push(...extractItems(content1));
  console.log(`✅ 1/${totalPages} (${allItems.length}건)`);

  for (let p = 2; p <= totalPages; p++) {
    await sleep(DELAY_MS);
    try {
      const data = await fetchPage(p);
      const content = data.body?.content || data.content || [];
      allItems.push(...extractItems(content));

      if (p % 10 === 0 || p === totalPages) {
        console.log(`✅ ${p}/${totalPages} (${allItems.length}건)`);
      }
    } catch (e) {
      console.error(`❌ 페이지 ${p} 실패: ${e.message}`);
      console.log("3초 후 재시도...");
      await sleep(3000);
      p--;
    }
  }

  console.log(`\n📊 수집 완료: ${allItems.length}건`);
  console.log(
    `  - 이미지 있음: ${allItems.filter((i) => i.imagePath).length}건`,
  );
  console.log(`  - 바코드 있음: ${allItems.filter((i) => i.barcode).length}건`);

  const blob = new Blob([JSON.stringify(allItems, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `supplier_hub_skus_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);

  console.log("💾 JSON 파일 다운로드 완료!");
  console.log("\n📋 샘플 데이터:");
  console.table(allItems.slice(0, 5));
})();
