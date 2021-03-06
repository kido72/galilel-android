package galilel.org.galilelwallet.ui.contacts_activity;

import android.content.Context;
import android.view.View;

import galilel.org.galilelwallet.R;
import global.AddressLabel;
import galilel.org.galilelwallet.ui.base.tools.adapter.BaseRecyclerAdapter;

public class ContactsAdapter extends BaseRecyclerAdapter<AddressLabel,ContactViewHolderBase> {

    public ContactsAdapter(Context context) {
        super(context);
    }

    @Override
    protected ContactViewHolderBase createHolder(View itemView, int type) {
        return new ContactViewHolderBase(itemView);
    }

    @Override
    protected int getCardViewResource(int type) {
        return R.layout.contact_row;
    }

    @Override
    protected void bindHolder(ContactViewHolderBase holder, AddressLabel data, int position) {
        holder.address.setText(data.getAddresses().get(0));
        holder.name.setText(data.getName());
    }


}
