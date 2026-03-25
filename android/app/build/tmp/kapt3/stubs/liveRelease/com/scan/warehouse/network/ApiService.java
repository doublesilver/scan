package com.scan.warehouse.network;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u00020\u00032\b\b\u0001\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u000e\u0010\u0007\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\bJ\u0018\u0010\t\u001a\u00020\n2\b\b\u0001\u0010\u000b\u001a\u00020\fH\u00a7@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u000e\u001a\u00020\u000f2\b\b\u0001\u0010\u000b\u001a\u00020\u0010H\u00a7@\u00a2\u0006\u0002\u0010\u0011J\u0018\u0010\u0012\u001a\u00020\u00132\b\b\u0001\u0010\u000b\u001a\u00020\u0014H\u00a7@\u00a2\u0006\u0002\u0010\u0015J\"\u0010\u0016\u001a\u00020\u00172\b\b\u0001\u0010\u0018\u001a\u00020\u00052\b\b\u0003\u0010\u0019\u001a\u00020\u001aH\u00a7@\u00a2\u0006\u0002\u0010\u001b\u00a8\u0006\u001c"}, d2 = {"Lcom/scan/warehouse/network/ApiService;", "", "getImage", "Lokhttp3/ResponseBody;", "path", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "healthCheck", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "login", "Lcom/scan/warehouse/model/LoginApiResponse;", "request", "Lcom/scan/warehouse/model/LoginRequest;", "(Lcom/scan/warehouse/model/LoginRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "refreshToken", "Lcom/scan/warehouse/model/RefreshApiResponse;", "Lcom/scan/warehouse/model/RefreshRequest;", "(Lcom/scan/warehouse/model/RefreshRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "scanBarcode", "Lcom/scan/warehouse/model/ScanApiResponse;", "Lcom/scan/warehouse/model/ScanRequest;", "(Lcom/scan/warehouse/model/ScanRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchProducts", "Lcom/scan/warehouse/model/SearchApiResponse;", "query", "limit", "", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_liveRelease"})
public abstract interface ApiService {
    
    @retrofit2.http.POST(value = "api/scan")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object scanBarcode(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.scan.warehouse.model.ScanRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.ScanApiResponse> $completion);
    
    @retrofit2.http.GET(value = "api/search")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object searchProducts(@retrofit2.http.Query(value = "q")
    @org.jetbrains.annotations.NotNull()
    java.lang.String query, @retrofit2.http.Query(value = "limit")
    int limit, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.SearchApiResponse> $completion);
    
    @retrofit2.http.GET(value = "api/images/proxy")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getImage(@retrofit2.http.Query(value = "path")
    @org.jetbrains.annotations.NotNull()
    java.lang.String path, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super okhttp3.ResponseBody> $completion);
    
    @retrofit2.http.GET(value = "health")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object healthCheck(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super okhttp3.ResponseBody> $completion);
    
    @retrofit2.http.POST(value = "api/auth/login")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object login(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.scan.warehouse.model.LoginRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.LoginApiResponse> $completion);
    
    @retrofit2.http.POST(value = "api/auth/refresh")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object refreshToken(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.scan.warehouse.model.RefreshRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.scan.warehouse.model.RefreshApiResponse> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}