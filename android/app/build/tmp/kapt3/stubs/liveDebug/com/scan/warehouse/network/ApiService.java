package com.scan.warehouse.network;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u00020\u00032\b\b\u0001\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u000e\u0010\u0007\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\bJ\u0018\u0010\t\u001a\u00020\n2\b\b\u0001\u0010\u000b\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\"\u0010\f\u001a\u00020\r2\b\b\u0001\u0010\u000e\u001a\u00020\u00052\b\b\u0003\u0010\u000f\u001a\u00020\u0010H\u00a7@\u00a2\u0006\u0002\u0010\u0011\u00a8\u0006\u0012"}, d2 = {"Lcom/scan/warehouse/network/ApiService;", "", "getImage", "Lokhttp3/ResponseBody;", "path", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "healthCheck", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scanBarcode", "Lcom/scan/warehouse/model/ScanResponse;", "barcode", "searchProducts", "Lcom/scan/warehouse/model/SearchResponse;", "query", "limit", "", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_liveDebug"})
public abstract interface ApiService {
    
    @retrofit2.http.GET(value = "api/scan/{barcode}")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object scanBarcode(@retrofit2.http.Path(value = "barcode")
    @org.jetbrains.annotations.NotNull()
    java.lang.String barcode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.ScanResponse> $completion);
    
    @retrofit2.http.GET(value = "api/search")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object searchProducts(@retrofit2.http.Query(value = "q")
    @org.jetbrains.annotations.NotNull()
    java.lang.String query, @retrofit2.http.Query(value = "limit")
    int limit, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.SearchResponse> $completion);
    
    @retrofit2.http.GET(value = "api/image/{path}")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getImage(@retrofit2.http.Path(value = "path", encoded = true)
    @org.jetbrains.annotations.NotNull()
    java.lang.String path, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super okhttp3.ResponseBody> $completion);
    
    @retrofit2.http.GET(value = "health")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object healthCheck(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super okhttp3.ResponseBody> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}