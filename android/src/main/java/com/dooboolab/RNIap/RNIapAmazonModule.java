package com.dooboolab.RNIap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.lang.NullPointerException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.PurchasingListener;

import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;

public class RNIapAmazonModule extends ReactContextBaseJavaModule {
  final String TAG = "RNIapAmazonModule";

  private PurchasingListener purchasingListener = new PurchasingListener() {
    @Override
    public void onUserDataResponse(UserDataResponse userDataResponse) {
      Log.d(TAG, "oudr=" + userDataResponse.toString());
    }

    @Override
    public void onProductDataResponse(ProductDataResponse response) {
      Log.d(TAG, "opdr=" + response.toString());
      final ProductDataResponse.RequestStatus status = response.getRequestStatus();
      Log.d(TAG, "onProductDataResponse status: " + status );

      switch (status) {
        case SUCCESSFUL:
          Log.d(TAG, "onProductDataResponse: successful.  The item data map in this response includes the valid SKUs");

          final Map<String, Product> productData = response.getProductData();

          final Set<String> unavailableSkus = response.getUnavailableSkus();
          Log.d(TAG, "onProductDataResponse: " + unavailableSkus.size() + " unavailable skus");
          Log.d(TAG, "unavailableSkus: " + unavailableSkus.toString());
          JSONArray items = new JSONArray();
          try {
            for (Map.Entry<String, Product> skuDetails : productData.entrySet()) {
              Product product=skuDetails.getValue();
              NumberFormat format = NumberFormat.getCurrencyInstance();

              Number number;
              try {
                number = format.parse(product.getPrice());
              } catch (ParseException e) {
                result.error(TAG, "Price Parsing error", e.getMessage());
                return;
              }
              JSONObject item = new JSONObject();
              item.put("productId", product.getSku());
              item.put("price", number.toString());
              item.put("currency", null);
              ProductType productType = product.getProductType();
              switch (productType) {
                case ENTITLED:
                case CONSUMABLE:
                  item.put("type", "inapp");
                  break;
                case SUBSCRIPTION:
                  item.put("type", "subs");
                  break;
              }
              item.put("localizedPrice", product.getPrice());
              item.put("title", product.getTitle());
              item.put("description", product.getDescription());
              item.put("introductoryPrice", "");
              item.put("subscriptionPeriodAndroid", "");
              item.put("freeTrialPeriodAndroid", "");
              item.put("introductoryPriceCyclesAndroid", "");
              item.put("introductoryPricePeriodAndroid", "");
              Log.d(TAG, "opdr Putting "+item.toString());
              items.put(item);
            }
            //System.err.println("Sending "+items.toString());
            result.success(items.toString());
          } catch (JSONException e) {
            result.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.getMessage());
          }
          break;
        case FAILED:
          result.error(TAG,"FAILED",null);
        case NOT_SUPPORTED:
          Log.d(TAG, "onProductDataResponse: failed, should retry request");
          result.error(TAG,"NOT_SUPPORTED",null);
          break;
      }
    }

    // buyItemByType
    @Override
    public void onPurchaseResponse(PurchaseResponse response) {
      Log.d(TAG, "opr="+response.toString());
      final PurchaseResponse.RequestStatus status = response.getRequestStatus();
      switch(status) {
        case SUCCESSFUL:
          Receipt receipt = response.getReceipt();
          PurchasingService.notifyFulfillment(receipt.getReceiptId(), FulfillmentResult.FULFILLED);
          Date date = receipt.getPurchaseDate();
          Long transactionDate=date.getTime();
          try {
            JSONObject item = getPurchaseData(receipt.getSku(),
                  receipt.getReceiptId(),
                  receipt.getReceiptId(),
                  transactionDate.doubleValue());
            Log.d(TAG, "opr Putting "+item.toString());
            result.success(item.toString());
          } catch (JSONException e) {
            result.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.getMessage());
          }
          break;
        case FAILED:
          result.error(TAG, "buyItemByType", "billingResponse is not ok: " + status);
          break;
      }
    }

    // getAvailableItemsByType
    @Override
    public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse response) {
      Log.d(TAG, "opudr="+response.toString());
      final PurchaseUpdatesResponse.RequestStatus status = response.getRequestStatus();

      switch(status) {
        case SUCCESSFUL:
          JSONArray items = new JSONArray();
          try {
            List<Receipt> receipts = response.getReceipts();
            for(Receipt receipt : receipts) {
              Date date = receipt.getPurchaseDate();
              Long transactionDate=date.getTime();
              JSONObject item = getPurchaseData(receipt.getSku(),
                      receipt.getReceiptId(),
                      receipt.getReceiptId(),
                      transactionDate.doubleValue());

              Log.d(TAG, "opudr Putting "+item.toString());
              items.put(item);
            }
            result.success(items.toString());
          } catch (JSONException e) {
            result.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.getMessage());
          }
          break;
        case FAILED:
          result.error(TAG,"FAILED",null);
          break;
        case NOT_SUPPORTED:
          Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request");
          result.error(TAG,"NOT_SUPPORTED",null);
          break;
      }
    }
  };
}