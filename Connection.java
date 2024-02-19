// Also useful
// Coded originally by SparkUniverse
// Decompiled apart of essential-authentication-vuln

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package gg.essential.network.connectionmanager;

import com.google.common.collect.Maps;
import com.google.common.primitives.Bytes;
import com.sparkuniverse.toolbox.relationships.enums.FriendRequestPrivacySetting;
import com.sparkuniverse.toolbox.relationships.enums.RelationshipState;
import com.sparkuniverse.toolbox.relationships.enums.RelationshipType;
import com.sparkuniverse.toolbox.relationships.serialisation.FriendRequestPrivacySettingTypeAdapter;
import com.sparkuniverse.toolbox.relationships.serialisation.RelationshipStateAdapter;
import com.sparkuniverse.toolbox.relationships.serialisation.RelationshipTypeAdapter;
import com.sparkuniverse.toolbox.serialization.DateTimeTypeAdapter;
import com.sparkuniverse.toolbox.serialization.UUIDTypeAdapter;
import com.sparkuniverse.toolbox.util.DateTime;
import gg.essential.Essential;
import gg.essential.api.utils.Multithreading;
import gg.essential.config.EssentialConfig;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.connection.ClientConnectionLoginPacket;
import gg.essential.connectionmanager.common.packet.connection.ConnectionRegisterPacketTypeIdPacket;
import gg.essential.data.OnboardingData;
import gg.essential.handlers.CertChain;
import gg.essential.lib.caffeine.cache.Cache;
import gg.essential.lib.caffeine.cache.Caffeine;
import gg.essential.lib.caffeine.cache.Expiry;
import gg.essential.lib.caffeine.cache.RemovalCause;
import gg.essential.lib.caffeine.cache.Scheduler;
import gg.essential.lib.gson.Gson;
import gg.essential.lib.gson.GsonBuilder;
import gg.essential.lib.gson.JsonParseException;
import gg.essential.lib.websocket.client.WebSocketClient;
import gg.essential.lib.websocket.handshake.ServerHandshake;
import gg.essential.network.connectionmanager.ConnectionManager.Status;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.connectionmanager.legacyjre.LegacyJre;
import gg.essential.network.connectionmanager.legacyjre.LegacyJreDnsResolver;
import gg.essential.network.connectionmanager.legacyjre.LegacyJreSocketFactory;
import gg.essential.universal.UMinecraft;
import gg.essential.util.ExtensionsKt;
import gg.essential.util.LimitedExecutor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Connection extends WebSocketClient {
    private final String PACKET_PACKAGE = "gg.essential.connectionmanager.common.packet.";
    private final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private final @NotNull Executor mainThreadExecutor = ExtensionsKt.getExecutor(UMinecraft.getMinecraft());
    private final @NotNull Executor sendExecutor = new LimitedExecutor(Multithreading.getPool(), 1, new ConcurrentLinkedQueue());
    private final @NotNull Map<Class<? extends Packet>, PacketHandler<?>> packetHandlers = Maps.newHashMap();
    private final @NotNull Cache<@NotNull UUID, @NotNull Pair<@NotNull Long, @NotNull Consumer<@NotNull Optional<Packet>>>> awaitingPacketResponses = Caffeine.newBuilder().maximumSize(10000L).executor(Multithreading.getPool()).scheduler(Scheduler.forScheduledExecutorService(Multithreading.getScheduledPool())).expireAfter(new Expiry<UUID, Pair<Long, Consumer<Optional<Packet>>>>() {
        public long expireAfterCreate(@NotNull UUID packetId, @NotNull Pair<@NotNull Long, @NotNull Consumer<@NotNull Optional<Packet>>> valueData, long currentTime) {
            return (Long)valueData.getKey();
        }

        public long expireAfterUpdate(@NotNull UUID packetId, @NotNull Pair<@NotNull Long, @NotNull Consumer<@NotNull Optional<Packet>>> valueData, long currentTime, long currentDuration) {
            return currentDuration;
        }

        public long expireAfterRead(@NotNull UUID packetId, @NotNull Pair<@NotNull Long, @NotNull Consumer<@NotNull Optional<Packet>>> valueData, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }).evictionListener((key, value, cause) -> {
        if (value != null && (RemovalCause.EXPIRED == cause || RemovalCause.SIZE == cause)) {
            Consumer<Optional<Packet>> packetHandler = (Consumer)value.getRight();
            this.mainThreadExecutor.execute(() -> {
                packetHandler.accept(Optional.empty());
            });
        }

    }).build();
    private final @NotNull AtomicInteger packetTypeId = new AtomicInteger();
    private final @NotNull Map<Integer, String> incomingPacketTypeIds = Maps.newConcurrentMap();
    private final @NotNull Map<String, Integer> outgoingPacketTypeIds = Maps.newConcurrentMap();
    private final boolean LOG_PACKETS = System.getProperty("essential.logPackets", "false").equals("true");
    private int failedConnects = 0;
    private final @NotNull Gson gson = (new GsonBuilder()).registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).registerTypeAdapter(RelationshipType.class, new RelationshipTypeAdapter()).registerTypeAdapter(RelationshipState.class, new RelationshipStateAdapter()).registerTypeAdapter(FriendRequestPrivacySetting.class, new FriendRequestPrivacySettingTypeAdapter()).registerTypeAdapter(DateTime.class, new DateTimeTypeAdapter()).create();
    private final @NotNull ConnectionManager connectionManager;
    private final @NotNull Lock connectLock = new ReentrantLock();
    private long lastReceivedKeepAlive;
    private long connectedAt = System.currentTimeMillis();
    private boolean connectedBefore = false;
    ScheduledFuture<?> retryConnectionTask;
    private @Nullable String closureExtraData;
    private @Nullable ClientConnectionLoginPacket loginPacket;
    private static final int MAX_PROTOCOL = 4;
    private int usingProtocol = 1;

    public Connection(@NotNull ConnectionManager connectionManager) {
        super(URI.create(System.getProperty("essential.cm.host", (String)System.getenv().getOrDefault("ESSENTIAL_CM_HOST", "wss://connect.essential.gg/v1"))));
        this.connectionManager = connectionManager;
        this.setTcpNoDelay(true);
        this.setReuseAddr(true);
        this.setConnectionLostTimeout(0);
        if (LegacyJre.IS_LEGACY_JRE_51) {
            Essential.logger.info("Using LegacyJreDnsResolver");
            this.setDnsResolver(new LegacyJreDnsResolver());
        } else {
            Essential.logger.info("Using Default JreDnsResolver");
        }

        Multithreading.getScheduledPool().scheduleAtFixedRate(() -> {
            if (this.isOpen()) {
                long diff = System.currentTimeMillis() - this.lastReceivedKeepAlive;
                if (diff >= 60000L) {
                    this.close(CloseReason.SERVER_KEEP_ALIVE_TIMEOUT, "" + diff + "ms");
                }
            }
        }, 0L, 30L, TimeUnit.SECONDS);
    }

    public void registerIncomingPacketTypeId(@NotNull String packetName, int packetTypeId) {
        this.incomingPacketTypeIds.put(packetTypeId, packetName);
    }

    public void setLastReceivedKeepAlive(long lastReceivedKeepAlive) {
        this.lastReceivedKeepAlive = lastReceivedKeepAlive;
    }

    public <T extends Packet> void registerPacketHandler(Class<T> cls, PacketHandler<T> handler) {
        this.packetHandlers.put(cls, handler);
    }

    public void close(@NotNull CloseReason closeReason) {
        this.close(closeReason, (String)null);
    }

    public void close(@NotNull CloseReason closeReason, @Nullable String extraData) {
        this.closureExtraData = extraData;
        this.close(closeReason.getCode(), closeReason.name());
    }

    public void onOpen(@NotNull ServerHandshake serverHandshake) {
        Essential.logger.info("Opened connection to Essential ConnectionManager (code={}, message={})", serverHandshake.getHttpStatus(), serverHandshake.getHttpStatusMessage());
        this.usingProtocol = Integer.parseInt(serverHandshake.getFieldValue("Essential-Protocol-Version"));

        assert this.loginPacket != null;

        this.closureExtraData = null;
        this.packetTypeId.set(0);
        this.incomingPacketTypeIds.clear();
        this.outgoingPacketTypeIds.clear();
        String packetName = this.splitPacketPackage(ConnectionRegisterPacketTypeIdPacket.class);
        this.incomingPacketTypeIds.put(0, packetName);
        this.outgoingPacketTypeIds.put(packetName, 0);
        this.connectedAt = System.currentTimeMillis();
        if (this.usingProtocol > 1) {
            this.mainThreadExecutor.execute(() -> {
                if (this.isOpen()) {
                    this.connectionManager.completeConnection();
                }
            });
        } else {
            this.connectionManager.onOpenAsync(this.loginPacket);
        }

    }

    public void onClose(int code, @NotNull String reason, boolean remote) {
        Essential.logger.info("Closed connection to Essential Connection Manager (code={}, reason={}, remote={}), connection was open for {}ms", code, reason + (this.closureExtraData == null ? "" : " (" + this.closureExtraData + ")"), remote, System.currentTimeMillis() - this.connectedAt);
        if (reason.contains("Invalid status code received: 410") || reason.contains("Invalid status code received: 404")) {
            this.connectionManager.outdated = true;
        }

        this.connectionManager.connectionStatus.complete(Status.GENERAL_FAILURE);
        Executor var10000 = this.mainThreadExecutor;
        ConnectionManager var10001 = this.connectionManager;
        Objects.requireNonNull(var10001);
        var10000.execute(var10001::onClose);
    }

    public void onMessage(@NotNull String message) {
    }

    public void onMessage(@NotNull ByteBuffer byteBuffer) {
        Packet packet;
        try {
            label91: {
                ByteArrayInputStream byteArrayInputStream;
                label90: {
                    label94: {
                        byteArrayInputStream = new ByteArrayInputStream(byteBuffer.array());

                        try {
                            label95: {
                                DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

                                label84: {
                                    label83: {
                                        try {
                                            int packetTypeId = dataInputStream.readInt();
                                            String packetName = (String)this.incomingPacketTypeIds.get(packetTypeId);
                                            if (packetName == null) {
                                                Essential.logger.warn("Unknown packet type id {} from connection manager.", packetTypeId);
                                                break label83;
                                            }

                                            Class packetClass;
                                            try {
                                                packetClass = Class.forName("gg.essential.connectionmanager.common.packet." + packetName);
                                            } catch (ClassNotFoundException var14) {
                                                packetClass = UnknownPacket.class;
                                            }

                                            String packetIdString = this.readString(dataInputStream);
                                            UUID packetId = null;
                                            if (!StringUtils.isEmpty(packetIdString)) {
                                                packetId = UUID.fromString(packetIdString);
                                            }

                                            String jsonString = this.readString(dataInputStream);
                                            if (this.LOG_PACKETS) {
                                                Essential.debug.info("" + packetId + " - " + packetClass.getSimpleName() + " " + jsonString);
                                            }

                                            try {
                                                packet = (Packet)this.gson.fromJson(jsonString, packetClass);
                                            } catch (JsonParseException var15) {
                                                Essential.logger.error("Error when deserialising json '{}' for '{}'.", jsonString, packetClass, var15);
                                                break label84;
                                            }

                                            if (packetId != null) {
                                                packet.setUniqueId(packetId);
                                            }
                                        } catch (Throwable var16) {
                                            try {
                                                dataInputStream.close();
                                            } catch (Throwable var13) {
                                                var16.addSuppressed(var13);
                                            }

                                            throw var16;
                                        }

                                        dataInputStream.close();
                                        break label95;
                                    }

                                    dataInputStream.close();
                                    break label90;
                                }

                                dataInputStream.close();
                                break label94;
                            }
                        } catch (Throwable var17) {
                            try {
                                byteArrayInputStream.close();
                            } catch (Throwable var12) {
                                var17.addSuppressed(var12);
                            }

                            throw var17;
                        }

                        byteArrayInputStream.close();
                        break label91;
                    }

                    byteArrayInputStream.close();
                    return;
                }

                byteArrayInputStream.close();
                return;
            }
        } catch (IOException var18) {
            Essential.logger.error("Error when reading byte buffer data '{}'.", byteBuffer.array(), var18);
            return;
        }

        this.onMessage(packet);
    }

    private void onMessage(Packet packet) {
        PacketHandler packetHandler = (PacketHandler)this.packetHandlers.get(packet.getClass());
        UUID packetId = packet.getPacketUniqueId();
        Consumer<Optional<Packet>> responseHandler = null;
        if (packetId != null) {
            Pair<Long, Consumer<Optional<Packet>>> responseCallbackPair = (Pair)this.awaitingPacketResponses.getIfPresent(packetId);
            if (responseCallbackPair != null) {
                this.awaitingPacketResponses.invalidate(packetId);
                responseHandler = (Consumer)responseCallbackPair.getRight();
            }
        }

        Consumer fResponseHandler;
        Consumer asyncResponseHandler;
        if (responseHandler instanceof AsyncResponseHandler) {
            fResponseHandler = null;
            asyncResponseHandler = responseHandler;
        } else {
            fResponseHandler = responseHandler;
            asyncResponseHandler = null;
        }

        if (packetHandler != null || responseHandler != null) {
            Runnable syncPacketHandler = null;
            if (packetHandler != null) {
                try {
                    syncPacketHandler = packetHandler.handleAsync(this.connectionManager, packet);
                } catch (Throwable var11) {
                    var11.printStackTrace();
                }
            }

            if (asyncResponseHandler != null) {
                try {
                    asyncResponseHandler.accept(Optional.of(packet));
                } catch (Throwable var10) {
                    var10.printStackTrace();
                }
            }

            this.mainThreadExecutor.execute(() -> {
                if (this.isOpen()) {
                    Consumer<Optional<Packet>> responseHandlerSync = fResponseHandler;
                    if (fResponseHandler instanceof EarlyResponseHandler) {
                        try {
                            responseHandlerSync.accept(Optional.of(packet));
                        } catch (Throwable var8) {
                            var8.printStackTrace();
                        }

                        responseHandlerSync = null;
                    }

                    if (syncPacketHandler != null) {
                        try {
                            syncPacketHandler.run();
                        } catch (Throwable var7) {
                            var7.printStackTrace();
                        }
                    }

                    if (responseHandlerSync != null) {
                        try {
                            responseHandlerSync.accept(Optional.of(packet));
                        } catch (Throwable var6) {
                            var6.printStackTrace();
                        }
                    }

                }
            });
        }
    }

    private void registerPacketCallback(@NotNull UUID packetId, @NotNull TimeUnit timeoutUnit, @NotNull Long timeoutValue, @NotNull Consumer<Optional<Packet>> responseCallback) {
        this.awaitingPacketResponses.put(packetId, Pair.of(timeoutUnit.toNanos(timeoutValue), responseCallback));
    }

    public void onError(@NotNull Exception e) {
        Essential.logger.error("Critical error occurred on connection management. ", e);
    }

    public void send(@NotNull Packet packet, @Nullable Consumer<Optional<Packet>> responseCallback, @Nullable TimeUnit timeoutUnit, @Nullable Long timeoutValue, @Nullable UUID packetId) {
        if (!this.isOpen()) {
            if (responseCallback != null) {
                responseCallback.accept(Optional.empty());
            }

        } else {
            boolean wantsResponseHandling = responseCallback != null && timeoutUnit != null && timeoutValue != null;
            packetId = wantsResponseHandling && packetId == null ? UUID.randomUUID() : packetId;
            if (wantsResponseHandling) {
                this.registerPacketCallback(packetId, timeoutUnit, timeoutValue, responseCallback);
            }

            Packet fakeReplyPacket = packet.getFakeReplyPacket();
            if (fakeReplyPacket != null) {
                fakeReplyPacket.setUniqueId(packetId);
                Multithreading.schedule(() -> {
                    this.onMessage(fakeReplyPacket);
                }, (long)packet.getFakeReplyDelayMs(), TimeUnit.MILLISECONDS);
            } else {
                this.sendExecutor.execute(() -> {
                    this.doSend(packet, packetId);
                });
            }
        }
    }

    private void doSend(Packet packet, @Nullable UUID packetId) {
        int packetTypeId = (Integer)this.outgoingPacketTypeIds.computeIfAbsent(this.splitPacketPackage(packet.getClass()), (packetName) -> {
            int newId = this.packetTypeId.incrementAndGet();
            this.doSend(new ConnectionRegisterPacketTypeIdPacket(packetName, newId), (UUID)null);
            return newId;
        });
        byte[] packetBytes = this.gson.toJson(packet).getBytes(StandardCharsets.UTF_8);
        byte[] packetIdBytes = packetId != null ? packetId.toString().getBytes(StandardCharsets.UTF_8) : this.EMPTY_BYTE_ARRAY;
        if (this.LOG_PACKETS) {
            Essential.debug.info("" + packetId + " - " + packet.getClass().getSimpleName() + " " + new String(packetBytes));
        }

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            try {
                DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

                try {
                    dataOutputStream.writeInt(packetTypeId);
                    dataOutputStream.writeInt(packetIdBytes.length);
                    dataOutputStream.write(packetIdBytes);
                    dataOutputStream.writeInt(packetBytes.length);
                    dataOutputStream.write(packetBytes);
                    this.send(byteArrayOutputStream.toByteArray());
                } catch (Throwable var12) {
                    try {
                        dataOutputStream.close();
                    } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                    }

                    throw var12;
                }

                dataOutputStream.close();
            } catch (Throwable var13) {
                try {
                    byteArrayOutputStream.close();
                } catch (Throwable var10) {
                    var13.addSuppressed(var10);
                }

                throw var13;
            }

            byteArrayOutputStream.close();
        } catch (IOException var14) {
            Essential.logger.error("Error occurred when sending out packet '{}'.", packet, var14);
        }

    }

    public void attemptConnect(boolean resetAttempts) {
        if (resetAttempts) {
            this.failedConnects = 0;
        }

        this.attemptConnect();
    }

    public void attemptConnect() {
        if (this.connectLock.tryLock()) {
            try {
                this.doAttemptConnect();
            } finally {
                this.connectLock.unlock();
            }

        }
    }

    // This is where ConnectionManager.java is used.
    private void doAttemptConnect() {
        this.clearHeaders();
        if (!OnboardingData.hasAcceptedTos()) {
            this.connectionManager.connectionStatus.complete(Status.NO_TOS);
        } else if (!EssentialConfig.INSTANCE.getEssentialEnabled()) {
            this.connectionManager.connectionStatus.complete(Status.ESSENTIAL_DISABLED);
        } else if (this.isOpen()) {
            this.connectionManager.connectionStatus.complete(Status.ALREADY_CONNECTED);
        } else {
            this.lastReceivedKeepAlive = System.currentTimeMillis();
            byte[] secret = this.connectionManager.prepareLoginAsync();
            if (secret == null) {
                this.connectionManager.connectionStatus.complete(Status.MOJANG_UNAUTHORIZED);
                this.failedConnects = Math.max(this.failedConnects, 6);
                this.retryConnectWithBackoff();
            } else {
                this.loginPacket = new ClientConnectionLoginPacket(this.connectionManager.getMinecraftHook().getPlayerName(), secret);
                String protocolProperty = System.getProperty("essential.cm.protocolVersion");
                if (protocolProperty == null || Integer.parseInt(protocolProperty) > 1) {
                    byte[] colon = ":".getBytes(StandardCharsets.UTF_8);
                    byte[] name = this.connectionManager.getMinecraftHook().getPlayerName().getBytes(StandardCharsets.UTF_8);
                    byte[] nameSecret = Bytes.concat(new byte[][]{name, colon, secret});
                    String encoded = Base64.getEncoder().encodeToString(nameSecret);
                    this.addHeader("Authorization", "Basic " + encoded);
                    if (protocolProperty == null) {
                        this.addHeader("Essential-Max-Protocol-Version", String.valueOf(4));
                    } else {
                        this.addHeader("Essential-Protocol-Version", protocolProperty);
                    }
                }

                try {
                    if (this.connectedBefore) {
                        super.reconnectBlocking();
                    } else {
                        SSLSocketFactory factory = ((SSLContext)(new CertChain()).loadEmbedded().done().getFirst()).getSocketFactory();
                        if (!LegacyJre.IS_LEGACY_JRE_51 && !LegacyJre.IS_LEGACY_JRE_74) {
                            Essential.logger.info("Using Default JreSocketFactory");
                        } else {
                            Essential.logger.info("Using LegacyJreSocketFactory");
                            factory = new LegacyJreSocketFactory((SSLSocketFactory)factory, this.uri.getHost());
                        }

                        if ("wss".equals(this.uri.getScheme())) {
                            this.setSocketFactory((SocketFactory)factory);
                        }

                        this.connectBlocking(5L, TimeUnit.SECONDS);
                        this.connectedBefore = true;
                        this.failedConnects = 0;
                    }
                } catch (Exception var7) {
                    this.connectedBefore = false;
                    Essential.logger.error("Error when connecting to Essential ConnectionManager.", var7);
                    var7.printStackTrace();
                }

                if (!this.isOpen()) {
                    Essential.logger.warn("Unable to connect to a Essential Connection Manager.");
                    this.connectionManager.connectionStatus.complete(Status.GENERAL_FAILURE);
                    this.retryConnectWithBackoff();
                } else {
                    Essential.logger.info("Essential Connection Manager connection established.");
                }
            }
        }
    }

    private void retryConnectWithBackoff() {
        ++this.failedConnects;
        if (this.retryConnectionTask != null) {
            this.retryConnectionTask.cancel(true);
        }

        this.retryConnectionTask = Multithreading.schedule(this::attemptConnect, (long)Math.min(Math.pow(2.0, (double)((float)(this.failedConnects + 3) + ThreadLocalRandom.current().nextFloat())), 128.0), TimeUnit.SECONDS);
    }

    private @NotNull String readString(@NotNull DataInputStream dataInputStream) throws IOException {
        byte[] bytes = new byte[dataInputStream.readInt()];
        dataInputStream.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private @NotNull String splitPacketPackage(@NotNull Class<? extends Packet> packetClass) {
        return packetClass.getName().replace("gg.essential.connectionmanager.common.packet.", "");
    }

    public Gson getGson() {
        return this.gson;
    }

    public interface AsyncResponseHandler extends Consumer<Optional<Packet>> {
    }

    public interface EarlyResponseHandler extends Consumer<Optional<Packet>> {
    }
}
