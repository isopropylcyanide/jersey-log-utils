package com.github.isopropylcyanide.jerseylogutils.model;

public class URIContext {

    private final String httpMethod;
    private final String path;

    public URIContext(String httpMethod, String path) {
        this.httpMethod = httpMethod;
        this.path = path;
    }

    public URIContext(String path) {
        this.path = path;
        this.httpMethod = null;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "ExcludeContext{" +
                "httpMethod='" + (httpMethod != null ? httpMethod : "*") + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
