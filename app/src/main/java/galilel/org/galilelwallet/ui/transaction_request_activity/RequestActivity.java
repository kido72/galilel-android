package galilel.org.galilelwallet.ui.transaction_request_activity;

import android.app.DialogFragment;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.support.v4.content.ContextCompat;

import com.google.zxing.WriterException;

import org.galilelj.core.Coin;
import org.galilelj.core.NetworkParameters;
import org.galilelj.core.Transaction;
import org.galilelj.uri.GalilelURI;

import galilel.org.galilelwallet.R;
import galilel.org.galilelwallet.ui.base.BaseActivity;
import galilel.org.galilelwallet.ui.base.dialogs.SimpleTextDialog;
import galilel.org.galilelwallet.ui.transaction_send_activity.AmountInputFragment;
import galilel.org.galilelwallet.utils.DialogsUtil;
import galilel.org.galilelwallet.utils.NavigationUtils;

import static galilel.org.galilelwallet.ui.qr_activity.MyAddressFragment.convertDpToPx;
import static galilel.org.galilelwallet.utils.QrUtils.encodeAsBitmap;

public class RequestActivity extends BaseActivity implements View.OnClickListener {

    private AmountInputFragment amountFragment;
    private EditText edit_memo;
    private String addressStr;
    private SimpleTextDialog errorDialog;

    private QrDialog qrDialog;

    @Override
    protected void onCreateView(Bundle savedInstanceState, ViewGroup container) {
        View root = getLayoutInflater().inflate(R.layout.fragment_transaction_request, container);
        setTitle(getString(R.string.payment_request));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        edit_memo = (EditText) root.findViewById(R.id.edit_memo);
        amountFragment = (AmountInputFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_amount);
        root.findViewById(R.id.btnRequest).setOnClickListener(this);

    }

    @Override
    public void onResume() {
        super.onResume();

        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        NavigationUtils.goBackToHome(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnRequest) {
            try {
                showRequestQr();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                showErrorDialog(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog(e.getMessage());
            }
        }
    }

    private void showRequestQr() throws Exception {
        // first check amount
        String amountStr = amountFragment.getAmountStr();
        if (amountStr.length() < 1) throw new IllegalArgumentException(getString(R.string.invalid_amount));
        if (amountStr.length() == 1 && amountStr.equals("."))
            throw new IllegalArgumentException(getString(R.string.invalid_amount));
        if (amountStr.charAt(0) == '.') {
            amountStr = getString(R.string.number_0) + amountStr;
        }

        Coin amount = Coin.parseCoin(amountStr);
        if (amount.isZero()) throw new IllegalArgumentException(getString(R.string.invalid_amount));
        if (amount.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
            throw new IllegalArgumentException(getString(R.string.invalid_amount_small) + " " + Transaction.MIN_NONDUST_OUTPUT.toFriendlyString());

        // memo
        String memo = edit_memo.getText().toString();

        addressStr = galilelModule.getFreshNewAddress().toBase58();

        NetworkParameters params = galilelModule.getConf().getNetworkParams();

        String galilelURI = GalilelURI.convertToBitcoinURI(
                params,
                addressStr,
                amount,
                getString(R.string.payment_request),
                memo
        );

        if (qrDialog != null){
            qrDialog = null;
        }
        qrDialog = QrDialog.newInstance(galilelURI);
        qrDialog.setQrText(galilelURI);
        qrDialog.show(getFragmentManager(),"qr_dialog");

    }

    private void showErrorDialog(int resStr) {
        showErrorDialog(getString(resStr));
    }

    private void showErrorDialog(String message) {
        if (errorDialog == null) {
            errorDialog = DialogsUtil.buildSimpleErrorTextDialog(this, getResources().getString(R.string.invalid_inputs), message);
        } else {
            errorDialog.setBody(message);
        }
        errorDialog.show(getFragmentManager(), getResources().getString(R.string.send_error_dialog_tag));
    }

    public static class QrDialog extends DialogFragment {

        private View root;
        private ImageView img_qr;
        private String qrText;

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            try {
                root = inflater.inflate(R.layout.qr_dialog, container);
                img_qr = (ImageView) root.findViewById(R.id.img_qr);
                root.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismiss();
                    }
                });
                updateQr();
            }catch (Exception e){
                Toast.makeText(getActivity(),getString(R.string.error_generic),Toast.LENGTH_SHORT).show();
                dismiss();
                getActivity().onBackPressed();
            }
            return root;
        }

        private void updateQr() throws WriterException {
            if (img_qr != null) {
                Resources r = getResources();
                int px = convertDpToPx(r, 225);
                Log.i("Util", qrText);
                Bitmap qrBitmap = encodeAsBitmap(qrText, px, px, ContextCompat.getColor(getContext(), R.color.galilelBlack), ContextCompat.getColor(getContext(), R.color.greyTemp));
                img_qr.setImageBitmap(qrBitmap);
            }
        }


        public void setQrText(String qrText) throws WriterException {
            this.qrText = qrText;
            updateQr();
        }

        public static QrDialog newInstance(String galilelURI) throws WriterException {
            QrDialog qrDialog = new QrDialog();
            qrDialog.setQrText(galilelURI);
            return qrDialog;
        }
    }

}
