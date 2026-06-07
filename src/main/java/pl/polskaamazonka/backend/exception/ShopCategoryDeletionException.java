package pl.polskaamazonka.backend.exception;

public class ShopCategoryDeletionException extends RuntimeException {

    public static final String ERROR_CODE = "SHOP_CATEGORY_LOCKED";

    public ShopCategoryDeletionException(String message) {
        super(message);
    }
}
