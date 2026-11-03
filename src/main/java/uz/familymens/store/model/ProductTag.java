package uz.familymens.store.model;

public enum ProductTag {
    NEW, SALE;

    public String key() {
        return name().toLowerCase();
    }
}
