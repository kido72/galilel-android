package galilel.org.galilelwallet.ui.wallet_activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionMenu;

import org.galilelj.core.Coin;
import org.galilelj.core.Transaction;
import org.galilelj.uri.GalilelURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import chain.BlockchainState;
import galilel.org.galilelwallet.R;
import global.exceptions.NoPeerConnectedException;
import global.GalilelRate;
import galilel.org.galilelwallet.ui.base.BaseDrawerActivity;
import galilel.org.galilelwallet.ui.base.dialogs.SimpleTextDialog;
import galilel.org.galilelwallet.ui.base.dialogs.SimpleTwoButtonsDialog;
import galilel.org.galilelwallet.ui.qr_activity.QrActivity;
import galilel.org.galilelwallet.ui.settings_backup_activity.SettingsBackupActivity;
import galilel.org.galilelwallet.ui.transaction_request_activity.RequestActivity;
import galilel.org.galilelwallet.ui.transaction_send_activity.SendActivity;
import galilel.org.galilelwallet.utils.AnimationUtils;
import galilel.org.galilelwallet.utils.DialogsUtil;
import galilel.org.galilelwallet.utils.scanner.ScanActivity;

import static android.Manifest.permission.CAMERA;
import static galilel.org.galilelwallet.service.IntentsConstants.ACTION_NOTIFICATION;
import static galilel.org.galilelwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_ON_COIN_RECEIVED;
import static galilel.org.galilelwallet.service.IntentsConstants.INTENT_BROADCAST_DATA_TYPE;
import static galilel.org.galilelwallet.ui.transaction_send_activity.SendActivity.INTENT_ADDRESS;
import static galilel.org.galilelwallet.ui.transaction_send_activity.SendActivity.INTENT_EXTRA_TOTAL_AMOUNT;
import static galilel.org.galilelwallet.ui.transaction_send_activity.SendActivity.INTENT_MEMO;
import static galilel.org.galilelwallet.utils.scanner.ScanActivity.INTENT_EXTRA_RESULT;

public class WalletActivity extends BaseDrawerActivity {

    private static final Logger log = LoggerFactory.getLogger(WalletActivity.class);

    private static final int SCANNER_RESULT = 122;

    private View root;
    private View container_txs;

    private TextView txt_value;
    private TextView txt_unnavailable;
    private TextView txt_local_currency;
    private TextView txt_watch_only;
    private View view_background;
    private View container_syncing;
    private GalilelRate galilelRate;
    private TransactionsFragmentBase txsFragment;

    // Receiver
    private LocalBroadcastManager localBroadcastManager;

    private IntentFilter galilelServiceFilter = new IntentFilter(ACTION_NOTIFICATION);
    private BroadcastReceiver galilelServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_NOTIFICATION)){
                if(intent.getStringExtra(INTENT_BROADCAST_DATA_TYPE).equals(INTENT_BROADCAST_DATA_ON_COIN_RECEIVED)){
                    // Check if the app is on foreground to update the view.
                    if (!isOnForeground)return;
                    updateBalance();
                    showOrHideSyncingContainer();
                    txsFragment.refresh();
                }
            }

        }
    };

    @Override
    protected void beforeCreate(){
        /*
        if (!appConf.isAppInit()){
            Intent intent = new Intent(this, SplashActivity.class);
            startActivity(intent);
            finish();
        }
        // show report dialog if something happen with the previous process
        */
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    protected void onCreateView(Bundle savedInstanceState, ViewGroup container) {
        setTitle(getString(R.string.my_wallet));
        root = getLayoutInflater().inflate(R.layout.fragment_wallet, container);
        View containerHeader = getLayoutInflater().inflate(R.layout.fragment_gali_amount,header_container);
        header_container.setVisibility(View.VISIBLE);
        txt_value = (TextView) containerHeader.findViewById(R.id.galilelValue);
        txt_unnavailable = (TextView) containerHeader.findViewById(R.id.txt_unnavailable);
        container_txs = root.findViewById(R.id.container_txs);
        txt_local_currency = (TextView) containerHeader.findViewById(R.id.txt_local_currency);
        txt_watch_only = (TextView) containerHeader.findViewById(R.id.txt_watch_only);
        view_background = root.findViewById(R.id.view_background);
        container_syncing = root.findViewById(R.id.container_syncing);
        // Open Send
        root.findViewById(R.id.fab_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (galilelModule.isWalletWatchOnly()){
                    Toast.makeText(v.getContext(),getString(R.string.error_watch_only_mode),Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(v.getContext(), SendActivity.class));
            }
        });
        root.findViewById(R.id.fab_request).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), RequestActivity.class));
            }
        });

        FloatingActionMenu floatingActionMenu = (FloatingActionMenu) root.findViewById(R.id.fab_menu);
        floatingActionMenu.setOnMenuToggleListener(new FloatingActionMenu.OnMenuToggleListener() {
            @Override
            public void onMenuToggle(boolean opened) {
                if (opened){
                    AnimationUtils.fadeInView(view_background,200);
                }else {
                    AnimationUtils.fadeOutGoneView(view_background,200);
                }
            }
        });

        txsFragment = (TransactionsFragmentBase) getSupportFragmentManager().findFragmentById(R.id.transactions_fragment);

    }

    @Override
    protected void onResume() {
        try {
            super.onResume();

            // to check current activity in the navigation drawer
            setNavigationMenuItemChecked(0);

            // register
            try {
                localBroadcastManager.unregisterReceiver(galilelServiceReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (galilelModule.isStarted()) {
                init();

                localBroadcastManager.registerReceiver(galilelServiceReceiver, galilelServiceFilter);

                updateState();
                updateBalance();
                showOrHideSyncingContainer();
                txsFragment.refresh();
            } else {
                log.info("This should open the loading screen first");
            }
        } catch (Exception e){
			LoggerFactory.getLogger(WalletActivity.class).error("Error on resume",e);
        }
    }

    private void updateState() {
        txt_watch_only.setVisibility(galilelModule.isWalletWatchOnly()?View.VISIBLE:View.GONE);
    }

    private void init() {
        // Start service if it's not started.
        galilelApplication.startGalilelService();

        if (!galilelApplication.getAppConf().hasBackup()){
            long now = System.currentTimeMillis();
            if (galilelApplication.getLastTimeRequestedBackup()+1800000L<now) {
                galilelApplication.setLastTimeBackupRequested(now);
                SimpleTwoButtonsDialog reminderDialog = DialogsUtil.buildSimpleTwoBtnsDialog(
                        this,
                        getString(R.string.reminder_backup),
                        getString(R.string.reminder_backup_body),
                        new SimpleTwoButtonsDialog.SimpleTwoBtnsDialogListener() {
                            @Override
                            public void onRightBtnClicked(SimpleTwoButtonsDialog dialog) {
                                startActivity(new Intent(WalletActivity.this, SettingsBackupActivity.class));
                                dialog.dismiss();
                            }

                            @Override
                            public void onLeftBtnClicked(SimpleTwoButtonsDialog dialog) {
                                dialog.dismiss();
                            }
                        }
                );
                reminderDialog.setLeftBtnText(getString(R.string.button_dismiss));
                reminderDialog.setLeftBtnTextColor(ContextCompat.getColor(getApplicationContext(), R.color.galilelBlack));
                reminderDialog.setRightBtnText(getString(R.string.button_ok));
                reminderDialog.show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // unregister
        //localBroadcastManager.unregisterReceiver(localReceiver);
        localBroadcastManager.unregisterReceiver(galilelServiceReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==R.id.action_qr){
            startActivity(new Intent(this, QrActivity.class));
            return true;
        }else if (item.getItemId()==R.id.action_scan){
            if (!checkPermission(CAMERA)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int permsRequestCode = 200;
                    String[] perms = {"android.permission.CAMERA"};
                    requestPermissions(perms, permsRequestCode);
                }
            }
            startActivityForResult(new Intent(this, ScanActivity.class),SCANNER_RESULT);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCANNER_RESULT){
            if (resultCode==RESULT_OK) {
                try {
                    String address = data.getStringExtra(INTENT_EXTRA_RESULT);
                    final String usedAddress;
                    if (galilelModule.chechAddress(address)){
                        usedAddress = address;
                    }else {
                        GalilelURI galilelUri = new GalilelURI(address);
                        usedAddress = galilelUri.getAddress().toBase58();
                        final Coin amount = galilelUri.getAmount();
                        if (amount != null){
                            final String memo = galilelUri.getMessage();
                            StringBuilder text = new StringBuilder();
                            text.append(getString(R.string.amount)).append(": ").append(amount.toFriendlyString());
                            if (memo != null){
                                text.append("\n").append(getString(R.string.description)).append(": ").append(memo);
                            }

                            SimpleTextDialog dialogFragment = DialogsUtil.buildSimpleTextDialog(this,
                                    getString(R.string.payment_request_received),
                                    text.toString())
                                .setOkBtnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Intent intent = new Intent(v.getContext(), SendActivity.class);
                                        intent.putExtra(INTENT_ADDRESS,usedAddress);
                                        intent.putExtra(INTENT_EXTRA_TOTAL_AMOUNT,amount);
                                        intent.putExtra(INTENT_MEMO,memo);
                                        startActivity(intent);
                                    }
                                });
                            dialogFragment.setImgAlertRes(R.drawable.ic_send_action);
                            dialogFragment.setAlignBody(SimpleTextDialog.Align.LEFT);
                            dialogFragment.setImgAlertRes(R.drawable.ic_fab_recieve);
                            dialogFragment.show(getFragmentManager(),"payment_request_dialog");
                            return;
                        }

                    }
                    DialogsUtil.showCreateAddressLabelDialog(this,usedAddress);
                }catch (Exception e){
                    e.printStackTrace();
                    Toast.makeText(this,getString(R.string.bad_address),Toast.LENGTH_LONG).show();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    private boolean checkPermission(String permission) {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),permission);

        return result == PackageManager.PERMISSION_GRANTED;
    }


    private void updateBalance() {
        Coin availableBalance = galilelModule.getAvailableBalanceCoin();
        txt_value.setText(!availableBalance.isZero()?availableBalance.toFriendlyString():getString(R.string.number_0) + " " + getString(R.string.set_currency));
        Coin unnavailableBalance = galilelModule.getUnnavailableBalanceCoin();
        txt_unnavailable.setText(!unnavailableBalance.isZero()?unnavailableBalance.toFriendlyString():getString(R.string.number_0) + " " + getString(R.string.set_currency));
        if (galilelRate == null)
            galilelRate = galilelModule.getRate(galilelApplication.getAppConf().getSelectedRateCoin());
        if (galilelRate!=null) {
            txt_local_currency.setText(
                    galilelApplication.getCentralFormats().format(
                            new BigDecimal(availableBalance.getValue() * galilelRate.getRate().doubleValue()).movePointLeft(8)
                    )
                    + " "+galilelRate.getCode()
            );
        }else {
            txt_local_currency.setText(getString(R.string.number_0));
        }
    }

    @Override
    protected void onBlockchainStateChange(){
        showOrHideSyncingContainer();
    }

    private void showOrHideSyncingContainer(){
        if (blockchainState == BlockchainState.SYNCING){
            AnimationUtils.fadeInView(container_syncing,500);
        }else if (blockchainState == BlockchainState.SYNC){
            AnimationUtils.fadeOutGoneView(container_syncing,500);
        }else if (blockchainState == BlockchainState.NOT_CONNECTION){
            AnimationUtils.fadeInView(container_syncing,500);
        }
    }
}
