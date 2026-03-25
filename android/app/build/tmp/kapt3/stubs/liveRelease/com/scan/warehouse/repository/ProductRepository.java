package com.scan.warehouse.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000l\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016H\u0082@\u00a2\u0006\u0002\u0010\u0017J\u0010\u0010\u0018\u001a\u00020\u00142\u0006\u0010\u0019\u001a\u00020\u0014H\u0016J\u001c\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00120\u001bH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u001c\u0010\u001dJ(\u0010\u001e\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\u001b\u0012\u0004\u0012\u00020 0\u001f2\u0006\u0010\u0013\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010!J2\u0010\"\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020#0\u001b\u0012\u0004\u0012\u00020 0\u001f2\u0006\u0010$\u001a\u00020\u00142\b\b\u0002\u0010%\u001a\u00020&H\u0096@\u00a2\u0006\u0002\u0010\'J\f\u0010(\u001a\u00020\u0016*\u00020)H\u0002J\f\u0010*\u001a\u00020+*\u00020)H\u0002R\u0014\u0010\u0005\u001a\u00020\u00068BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\u0002\u001a\u00020\u0003X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0014\u0010\u000b\u001a\u00020\f8BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006,"}, d2 = {"Lcom/scan/warehouse/repository/ProductRepository;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "api", "Lcom/scan/warehouse/network/ApiService;", "getApi", "()Lcom/scan/warehouse/network/ApiService;", "getContext", "()Landroid/content/Context;", "dao", "Lcom/scan/warehouse/db/ProductDao;", "getDao", "()Lcom/scan/warehouse/db/ProductDao;", "gson", "Lcom/google/gson/Gson;", "cacheProduct", "", "barcode", "", "response", "Lcom/scan/warehouse/model/ScanResponse;", "(Ljava/lang/String;Lcom/scan/warehouse/model/ScanResponse;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getImageUrl", "filePath", "healthCheck", "Lkotlin/Result;", "healthCheck-IoAF18A", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scanBarcode", "Lkotlin/Pair;", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchProducts", "Lcom/scan/warehouse/model/SearchResponse;", "query", "limit", "", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toScanResponse", "Lcom/scan/warehouse/db/CachedProduct;", "toSearchItem", "Lcom/scan/warehouse/model/SearchItem;", "app_liveRelease"})
public class ProductRepository {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.google.gson.Gson gson = null;
    
    public ProductRepository(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    protected final android.content.Context getContext() {
        return null;
    }
    
    private final com.scan.warehouse.network.ApiService getApi() {
        return null;
    }
    
    private final com.scan.warehouse.db.ProductDao getDao() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object scanBarcode(@org.jetbrains.annotations.NotNull()
    java.lang.String barcode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Pair<kotlin.Result<com.scan.warehouse.model.ScanResponse>, java.lang.Boolean>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object searchProducts(@org.jetbrains.annotations.NotNull()
    java.lang.String query, int limit, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Pair<kotlin.Result<com.scan.warehouse.model.SearchResponse>, java.lang.Boolean>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public java.lang.String getImageUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String filePath) {
        return null;
    }
    
    private final java.lang.Object cacheProduct(java.lang.String barcode, com.scan.warehouse.model.ScanResponse response, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final com.scan.warehouse.model.ScanResponse toScanResponse(com.scan.warehouse.db.CachedProduct $this$toScanResponse) {
        return null;
    }
    
    private final com.scan.warehouse.model.SearchItem toSearchItem(com.scan.warehouse.db.CachedProduct $this$toSearchItem) {
        return null;
    }
}