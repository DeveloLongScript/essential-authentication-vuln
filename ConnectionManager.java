//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
// essential-authentication-vuln decompiled
// code originally made by SparkUniverse

package gg.essential.network.connectionmanager;

import gg.essential.Essential;
import gg.essential.api.utils.Multithreading;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.connection.ClientConnectionDisconnectPacket;
import gg.essential.connectionmanager.common.packet.connection.ClientConnectionLoginPacket;
import gg.essential.connectionmanager.common.packet.connection.ConnectionKeepAlivePacket;
import gg.essential.connectionmanager.common.packet.connection.ConnectionRegisterPacketTypeIdPacket;
import gg.essential.connectionmanager.common.packet.connection.ServerConnectionReconnectPacket;
import gg.essential.connectionmanager.common.packet.multiplayer.ServerMultiplayerJoinServerPacket;
import gg.essential.connectionmanager.common.packet.relationships.ServerUuidNameMapPacket;
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket;
import gg.essential.connectionmanager.common.util.LoginUtil;
import gg.essential.event.client.PostInitializationEvent;
import gg.essential.event.client.ReAuthEvent;
import gg.essential.event.essential.TosAcceptedEvent;
import gg.essential.gui.elementa.state.v2.combinators.StateKt;
import gg.essential.gui.menu.AccountManager;
import gg.essential.gui.wardrobe.Item;
import gg.essential.lib.kbrewster.eventbus.Subscribe;
import gg.essential.network.client.MinecraftHook;
import gg.essential.network.connectionmanager.chat.ChatManager;
import gg.essential.network.connectionmanager.coins.CoinsManager;
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager;
import gg.essential.network.connectionmanager.cosmetics.OutfitManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.connectionmanager.handler.connection.ClientConnectionDisconnectPacketHandler;
import gg.essential.network.connectionmanager.handler.connection.ConnectionRegisterPacketTypeIdPacketHandler;
import gg.essential.network.connectionmanager.handler.connection.ServerConnectionKeepAlivePacketHandler;
import gg.essential.network.connectionmanager.handler.connection.ServerConnectionReconnectPacketHandler;
import gg.essential.network.connectionmanager.handler.mojang.ServerUuidNameMapPacketHandler;
import gg.essential.network.connectionmanager.handler.multiplayer.ServerMultiplayerJoinServerPacketHandler;
import gg.essential.network.connectionmanager.ice.IceManager;
import gg.essential.network.connectionmanager.media.ScreenshotManager;
import gg.essential.network.connectionmanager.notices.NoticesManager;
import gg.essential.network.connectionmanager.profile.ProfileManager;
import gg.essential.network.connectionmanager.relationship.RelationshipManager;
import gg.essential.network.connectionmanager.serverdiscovery.ServerDiscoveryManager;
import gg.essential.network.connectionmanager.skins.SkinsManager;
import gg.essential.network.connectionmanager.social.SocialManager;
import gg.essential.network.connectionmanager.sps.SPSManager;
import gg.essential.network.connectionmanager.subscription.SubscriptionManager;
import gg.essential.network.connectionmanager.telemetry.TelemetryManager;
import gg.essential.util.ModLoaderUtil;
import gg.essential.util.lwjgl3.Lwjgl3Loader;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import kotlin.Unit;
import kotlin.collections.MapsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConnectionManager {
    private final @NotNull Connection connection = new Connection(this);
    private final @NotNull MinecraftHook minecraftHook;
    private final @NotNull List<NetworkedManager> managers = new ArrayList();
    private final @NotNull NoticesManager noticesManager;
    private final @NotNull SubscriptionManager subscriptionManager;
    private final @NotNull RelationshipManager relationshipManager;
    private final @NotNull CosmeticsManager cosmeticsManager;
    private final @NotNull ChatManager chatManager;
    private final @NotNull ProfileManager profileManager;
    private final @NotNull SPSManager spsManager;
    private final @NotNull ServerDiscoveryManager serverDiscoveryManager;
    private final @NotNull SocialManager socialManager;
    private final @NotNull IceManager iceManager;
    private final @NotNull ScreenshotManager screenshotManager;
    private final @NotNull TelemetryManager telemetryManager;
    private CoinsManager coinsManager;
    private SkinsManager skinsManager;
    private final @NotNull OutfitManager outfitManager;
    private boolean triedReauth = false;
    private boolean authenticated = false;
    private boolean attemptingReconnect = false;
    private UUID lastAuthenticatedUUID;
    private boolean modsLoaded = false;
    private boolean modsSent = false;
    public CompletableFuture<Status> connectionStatus;
    public boolean outdated = false;

    public ConnectionManager(@NotNull MinecraftHook minecraftHook, File baseDir, Lwjgl3Loader lwjgl3) {
        this.minecraftHook = minecraftHook;
        this.subscriptionManager = new SubscriptionManager(this);
        this.managers.add(this.subscriptionManager);
        this.registerPacketHandler(ConnectionRegisterPacketTypeIdPacket.class, new ConnectionRegisterPacketTypeIdPacketHandler());
        this.registerPacketHandler(ConnectionKeepAlivePacket.class, new ServerConnectionKeepAlivePacketHandler());
        this.registerPacketHandler(ClientConnectionDisconnectPacket.class, new ClientConnectionDisconnectPacketHandler());
        this.registerPacketHandler(ServerConnectionReconnectPacket.class, new ServerConnectionReconnectPacketHandler());
        this.registerPacketHandler(ServerMultiplayerJoinServerPacket.class, new ServerMultiplayerJoinServerPacketHandler());
        this.registerPacketHandler(ServerUuidNameMapPacket.class, new ServerUuidNameMapPacketHandler());
        this.managers.add(this.noticesManager = new NoticesManager(this));
        this.cosmeticsManager = new CosmeticsManager(this, baseDir);
        this.managers.add(this.cosmeticsManager);
        this.relationshipManager = new RelationshipManager(this);
        this.managers.add(this.relationshipManager);
        this.chatManager = new ChatManager(this);
        this.managers.add(this.chatManager);
        this.profileManager = new ProfileManager(this);
        this.managers.add(this.profileManager);
        this.spsManager = new SPSManager(this);
        this.managers.add(this.spsManager);
        this.serverDiscoveryManager = new ServerDiscoveryManager(this);
        this.managers.add(this.serverDiscoveryManager);
        this.managers.add(this.socialManager = new SocialManager(this));
        this.managers.add(this.iceManager = new IceManager(this, this.spsManager));
        this.managers.add(this.screenshotManager = new ScreenshotManager(this, baseDir, lwjgl3));
        this.managers.add(this.telemetryManager = new TelemetryManager(this));
        this.managers.add(this.coinsManager = new CoinsManager(this));
        this.managers.add(this.skinsManager = new SkinsManager(this));
        this.outfitManager = new OutfitManager(this, this.cosmeticsManager, StateKt.map(this.skinsManager.getSkins(), (map) -> {
            return MapsKt.mapValues(map, (it) -> {
                return ((Item.SkinItem)it.getValue()).getSkin();
            });
        }));
        this.managers.add(this.outfitManager);
    }

    public @NotNull Connection getConnection() {
        return this.connection;
    }

    public @NotNull MinecraftHook getMinecraftHook() {
        return this.minecraftHook;
    }

    public @NotNull NoticesManager getNoticesManager() {
        return this.noticesManager;
    }

    public @NotNull SubscriptionManager getSubscriptionManager() {
        return this.subscriptionManager;
    }

    public @NotNull RelationshipManager getRelationshipManager() {
        return this.relationshipManager;
    }

    public @NotNull CosmeticsManager getCosmeticsManager() {
        return this.cosmeticsManager;
    }

    public @NotNull ChatManager getChatManager() {
        return this.chatManager;
    }

    public @NotNull ProfileManager getProfileManager() {
        return this.profileManager;
    }

    public @NotNull SPSManager getSpsManager() {
        return this.spsManager;
    }

    public @NotNull SocialManager getSocialManager() {
        return this.socialManager;
    }

    public @NotNull ScreenshotManager getScreenshotManager() {
        return this.screenshotManager;
    }

    public @NotNull IceManager getIceManager() {
        return this.iceManager;
    }

    public @NotNull TelemetryManager getTelemetryManager() {
        return this.telemetryManager;
    }

    public @NotNull CoinsManager getCoinsManager() {
        return this.coinsManager;
    }

    public @NotNull SkinsManager getSkinsManager() {
        return this.skinsManager;
    }

    public @NotNull OutfitManager getOutfitManager() {
        return this.outfitManager;
    }

    public boolean isOpen() {
        return this.connection.isOpen();
    }

    public boolean isAuthenticated() {
        return this.authenticated;
    }

    public <T extends Packet> void registerPacketHandler(Class<T> cls, PacketHandler<T> handler) {
        this.connection.registerPacketHandler(cls, handler);
    }
    
    // This is where the authentication process is! aka where the vulneribility is
    public @Nullable byte[] prepareLoginAsync() {
        this.authenticated = false;
        byte[] sharedSecret = LoginUtil.generateSharedSecret();
        String sessionHash = LoginUtil.computeHash(sharedSecret);
        int statusCode = gg.essential.util.LoginUtil.joinServer(this.minecraftHook.getSession(), this.minecraftHook.getPlayerUUID().toString().replace("-", ""), sessionHash);
        if (statusCode == 204) {
            return sharedSecret;
        } else {
            Essential.logger.warn("Could not authenticate with Mojang ({}) - connection attempt aborted.", statusCode);
            if (statusCode == 429 && !this.attemptingReconnect) {
                this.attemptingReconnect = true;
                Multithreading.schedule(() -> {
                    if (!this.triedReauth && !this.isOpen()) {
                        this.triedReauth = true;
                        this.connect();
                    }

                    this.attemptingReconnect = false;
                }, 5L, TimeUnit.SECONDS);
            } else if (!this.triedReauth) {
                this.triedReauth = true;
                Essential.logger.warn("Trying to refresh session token..");
                AccountManager.refreshCurrentSession(false, (session, error) -> {
                    if (error == null) {
                        this.connect();
                    }

                    return Unit.INSTANCE;
                });
            }

            return null;
        }
    }

    public void onOpenAsync(@NotNull ClientConnectionLoginPacket loginPacket) {
        this.send(loginPacket, (response) -> {
            if (!response.isPresent()) {
                Essential.logger.warn("Login request got no response - closing connection.");
                this.close(CloseReason.LOGIN_REQUEST_NO_RESPONSE);
                this.connectionStatus.complete(ConnectionManager.Status.NO_RESPONSE);
            } else {
                Packet packet = (Packet)response.get();
                if (!(packet instanceof ResponseActionPacket)) {
                    Essential.logger.warn("Login response type ({}) was not expected - closing connection ({}).", packet.getClass(), packet);
                    this.close(CloseReason.INVALID_LOGIN_RESPONSE);
                    this.connectionStatus.complete(ConnectionManager.Status.INVALID_RESPONSE);
                } else {
                    ResponseActionPacket responseActionPacket = (ResponseActionPacket)packet;
                    if (!responseActionPacket.isSuccessful()) {
                        Essential.logger.warn("Login attempt was not successful - closing connection.");
                        this.close(CloseReason.LOGIN_REQUEST_FAILED);
                        this.connectionStatus.complete(ConnectionManager.Status.GENERAL_FAILURE);
                    } else {
                        this.completeConnection();
                    }
                }
            }
        });
    }

    public void completeConnection() {
        this.triedReauth = false;
        this.authenticated = true;
        Iterator var1 = this.managers.iterator();

        while(var1.hasNext()) {
            NetworkedManager manager = (NetworkedManager)var1.next();
            manager.onConnected();
        }

        if (this.modsLoaded && !this.modsSent) {
            Multithreading.runAsync(() -> {
                this.send(ModLoaderUtil.createModsAnnouncePacket());
            });
            this.modsSent = true;
        }

        this.connectionStatus.complete(ConnectionManager.Status.SUCCESS);
    }

    public void onClose() {
        this.authenticated = false;
        this.modsSent = false;
        Iterator var1 = this.managers.iterator();

        while(var1.hasNext()) {
            NetworkedManager manager = (NetworkedManager)var1.next();
            manager.onDisconnect();
        }

        if (!this.outdated) {
            int delay = 30;
            if (this.connection.getURI().toString().contains("localhost")) {
                delay = 3;
            }

            Multithreading.schedule(() -> {
                if (!this.isOpen() && !this.attemptingReconnect) {
                    this.connect();
                }

            }, (long)delay, TimeUnit.SECONDS);
        }
    }

    public void respond(@NotNull Packet respondingTo, @NotNull Packet respondingWith) {
        this.send(respondingWith, (Consumer)null, (TimeUnit)null, (Long)null, respondingTo.getPacketUniqueId());
    }

    public void send(@NotNull Packet packet) {
        this.send(packet, (Consumer)null);
    }

    public void send(@NotNull Packet packet, @Nullable Consumer<Optional<Packet>> responseCallback) {
        this.send(packet, responseCallback, TimeUnit.SECONDS, 10L);
    }

    public void send(@NotNull Packet packet, @Nullable Consumer<Optional<Packet>> responseCallback, @Nullable TimeUnit timeoutUnit, @Nullable Long timeoutValue) {
        this.send(packet, responseCallback, timeoutUnit, timeoutValue, (UUID)null);
    }

    public void send(@NotNull Packet packet, @Nullable Consumer<Optional<Packet>> responseCallback, @Nullable TimeUnit timeoutUnit, @Nullable Long timeoutValue, @Nullable UUID packetId) {
        this.connection.send(packet, responseCallback, timeoutUnit, timeoutValue, packetId);
    }

    public synchronized CompletionStage<Status> connect() {
        if (this.connectionStatus != null && !this.connectionStatus.isDone()) {
            return this.connectionStatus;
        } else {
            this.connectionStatus = new CompletableFuture();
            Multithreading.getScheduledPool().execute(() -> {
                if (this.minecraftHook.getSession().equals("undefined")) {
                    AccountManager.refreshCurrentSession(false, (session, error) -> {
                        this.connection.attemptConnect(true);
                        return Unit.INSTANCE;
                    });
                } else {
                    this.connection.attemptConnect(true);
                }

            });
            return this.connectionStatus;
        }
    }

    public void close(@NotNull CloseReason closeReason) {
        this.close(closeReason, (String)null);
    }

    public void close(@NotNull CloseReason closeReason, @Nullable String metadata) {
        this.connection.close(closeReason, metadata);
    }

    @Subscribe
    public void onPostInit(PostInitializationEvent event) {
        this.modsLoaded = true;
        if (!this.modsSent && this.isAuthenticated()) {
            Multithreading.runAsync(() -> {
                this.send(ModLoaderUtil.createModsAnnouncePacket());
            });
            this.modsSent = true;
        }

    }

    @Subscribe
    public void onTosAcceptedEvent(@NotNull TosAcceptedEvent event) {
        this.connect();
    }

    @Subscribe
    public void onReAuthEvent(@NotNull ReAuthEvent event) {
        if (!event.getSession().getUuid().equals(this.lastAuthenticatedUUID)) {
            this.lastAuthenticatedUUID = event.getSession().getUuid();
            if (this.isOpen()) {
                this.close(CloseReason.REAUTHENTICATION);
            }

            this.connect();
        }

    }

    public ServerDiscoveryManager getServerDiscoveryManager() {
        return this.serverDiscoveryManager;
    }

    public void onTosRevokedOrEssentialDisabled() {
        if (this.isOpen()) {
            this.close(CloseReason.USER_TOS_REVOKED);
        }

        Iterator var1 = this.managers.iterator();

        while(var1.hasNext()) {
            NetworkedManager manager = (NetworkedManager)var1.next();
            manager.resetState();
        }

    }

    public static enum Status {
        NO_TOS,
        ESSENTIAL_DISABLED,
        ALREADY_CONNECTED,
        NO_RESPONSE,
        INVALID_RESPONSE,
        MOJANG_UNAUTHORIZED,
        GENERAL_FAILURE,
        SUCCESS;

        private Status() {
        }
    }
}
