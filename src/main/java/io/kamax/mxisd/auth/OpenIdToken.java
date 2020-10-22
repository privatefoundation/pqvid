package io.kamax.mxisd.auth;

import com.google.gson.annotations.SerializedName;

public class OpenIdToken {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("matrix_server_name")
    private String matrixServerName;

    @SerializedName("expires_in")
    private long expiresIn;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMatrixServerName() {
        return matrixServerName;
    }

    public void setMatrixServerName(String matrixServerName) {
        this.matrixServerName = matrixServerName;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
