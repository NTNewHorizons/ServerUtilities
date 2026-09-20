package serverutils.net;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import serverutils.client.tab.TabChannelHandler;
import serverutils.lib.io.DataIn;
import serverutils.lib.io.DataOut;
import serverutils.lib.net.MessageToClient;
import serverutils.lib.net.NetworkWrapper;

public class MessageTabHeaderFooter extends MessageToClient {

    private String header = "";
    private String footer = "";

    public MessageTabHeaderFooter() {}

    public MessageTabHeaderFooter(String header, String footer) {
        this.header = header == null ? "" : header;
        this.footer = footer == null ? "" : footer;
    }

    @Override
    public NetworkWrapper getWrapper() {
        return ServerUtilitiesNetHandler.GENERAL;
    }

    @Override
    public void writeData(DataOut data) {
        data.writeString(header == null ? "" : header);
        data.writeString(footer == null ? "" : footer);
    }

    @Override
    public void readData(DataIn data) {
        header = data.readString();
        footer = data.readString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onMessage() {
        TabChannelHandler.INSTANCE.setServerData(header, footer);
    }
}
