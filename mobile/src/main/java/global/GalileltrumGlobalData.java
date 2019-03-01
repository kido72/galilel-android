package global;

import java.util.ArrayList;
import java.util.List;

import galileltrum.GalileltrumPeerData;

public class GalileltrumGlobalData {

    public static final String[] TRUSTED_NODES = new String[] {
        "seed8.galilel.cloud"
    };

    public static final List<GalileltrumPeerData> listTrustedHosts() {
        List<GalileltrumPeerData> list = new ArrayList<>();
        for (String trustedNode : TRUSTED_NODES) {
            list.add(new GalileltrumPeerData(trustedNode,36001,55552));
        }
        return list;
    }
}
