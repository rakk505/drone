package com.modernity.drone;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneOperatorEntity;
import com.modernity.drone.entity.DroppedPayloadEntity;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.item.DroneItem;
import com.modernity.drone.item.BatteryItem;
import com.modernity.drone.item.BombItem;
import com.modernity.drone.item.FpvGogglesItem;
import com.modernity.drone.item.RemoteControlItem;
import com.modernity.drone.item.ShiftTooltipItem;
import com.modernity.drone.network.DroneNetwork;
import com.modernity.drone.test.DroneGameTests;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(DroneMod.MOD_ID)
public final class DroneMod {
    public static final String MOD_ID = "fpvdrone";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final TicketController DRONE_CHUNK_TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(MOD_ID, "active_drone"));
    public static final TicketController PILOT_BODY_CHUNK_TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(MOD_ID, "fpv_pilot_body"),
            (level, tickets) -> tickets.getEntityTickets().keySet().forEach(tickets::removeAllTickets)
    );

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    public static final DeferredItem<DroneItem> DRONE = ITEMS.registerItem(
            "drone",
            properties -> new DroneItem(properties, false),
            properties -> properties.stacksTo(1)
    );
    /** Old reconstruction name retained as a source compatibility alias. */
    public static final DeferredItem<DroneItem> MOSQUITO_DRONE = DRONE;
    public static final DeferredItem<DroneItem> THERMAL_DRONE = ITEMS.registerItem(
            "thermal_drone",
            properties -> new DroneItem(properties, true),
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<RemoteControlItem> REMOTE_CONTROL = ITEMS.registerItem(
            "remote_control", RemoteControlItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<FpvGogglesItem> FPV_GOGGLES = ITEMS.registerItem(
            "fpv_goggles", FpvGogglesItem::new,
            properties -> properties.stacksTo(1).equippable(EquipmentSlot.HEAD));
    public static final DeferredItem<BatteryItem> BATTERY = ITEMS.registerItem(
            "battery", BatteryItem::new, properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> ANTENNA = ITEMS.registerSimpleItem("antenna", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> CAMERA = ITEMS.registerSimpleItem("camera", properties -> properties.stacksTo(8));
    public static final DeferredItem<BombItem> BOMB_1 = ITEMS.registerItem(
            "bomb_1", properties -> new BombItem(properties, 1), properties -> properties.stacksTo(16));
    public static final DeferredItem<BombItem> BOMB_2 = ITEMS.registerItem(
            "bomb_2", properties -> new BombItem(properties, 2), properties -> properties.stacksTo(16));
    public static final DeferredItem<BombItem> BOMB_3 = ITEMS.registerItem(
            "bomb_3", properties -> new BombItem(properties, 3), properties -> properties.stacksTo(16));
    public static final DeferredItem<ShiftTooltipItem> BETAFLIGHT = ITEMS.registerItem(
            "betaflight",
            properties -> new ShiftTooltipItem(properties, "item.fpvdrone.betaflight.usage"),
            properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> WEIGHT = ITEMS.registerSimpleItem("weight", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> THERMAL = ITEMS.registerSimpleItem("thermal", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> ELECTRIC_MOTOR = ITEMS.registerSimpleItem("electric_motor", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> PROPELLER = ITEMS.registerSimpleItem("propeller", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> PROCESSOR = ITEMS.registerSimpleItem("processor", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> PIVOT_CONTROLLER = ITEMS.registerSimpleItem("pivot_controller", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> BOMB_HOLDER = ITEMS.registerSimpleItem("bomb_holder", properties -> properties.stacksTo(8));
    public static final DeferredItem<DroneItem> PAYLOAD_DRONE = ITEMS.registerItem(
            "mavic_drone",
            properties -> new DroneItem(properties, false),
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<BatteryItem> FPV_BATTERY = BATTERY;
    public static final DeferredItem<Item> DJI_BATTERY = ITEMS.registerSimpleItem("battery_dji", properties -> properties.durability(1000));
    public static final DeferredItem<RemoteControlItem> FPV_CONTROLLER = REMOTE_CONTROL;
    public static final DeferredItem<Item> DJI_CONTROLLER = ITEMS.registerSimpleItem("dji_controller", properties -> properties.stacksTo(1));
    public static final DeferredItem<ShiftTooltipItem> RPG_WARHEAD = ITEMS.registerItem(
            "rpg7",
            properties -> new ShiftTooltipItem(properties, "item.fpvdrone.rpg7.usage"),
            properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> FORTY_MM_PAYLOAD = ITEMS.registerSimpleItem("40mm_explosive", properties -> properties.stacksTo(16));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE_ENTITY = ENTITIES.registerEntityType(
            "drone",
            DroneEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.85F, 0.55F)
                    .eyeHeight(0.25F)
                    // EntityType stores this value in chunks. The configured
                    // observer distance is applied in DroneTrackingRangeMixin.
                    .clientTrackingRange(4)
                    .updateInterval(1)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<DroppedPayloadEntity>> DROPPED_PAYLOAD_ENTITY =
            ENTITIES.registerEntityType(
                    "dropped_payload",
                    DroppedPayloadEntity::new,
                    MobCategory.MISC,
                    builder -> builder.sized(0.24F, 0.42F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<DroneOperatorEntity>> DRONE_OPERATOR_ENTITY =
            ENTITIES.registerEntityType(
                    "drone_operator",
                    DroneOperatorEntity::new,
                    MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 1.95F)
                            .eyeHeight(1.65F)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .notInPeaceful()
            );

    public static final DeferredItem<SpawnEggItem> DRONE_OPERATOR_SPAWN_EGG = ITEMS.registerItem(
            "drone_operator_spawn_egg",
            SpawnEggItem::new,
            properties -> properties.spawnEgg(DRONE_OPERATOR_ENTITY.get())
    );

    static {
        // Preserve entities and the operator egg from worlds made by the pre-port
        // `drone` namespace. The autonomous drone must migrate with its operator.
        ENTITIES.addAlias(
                Identifier.fromNamespaceAndPath("drone", "drone"),
                Identifier.fromNamespaceAndPath(MOD_ID, "drone")
        );
        ENTITIES.addAlias(
                Identifier.fromNamespaceAndPath("drone", "dropped_payload"),
                Identifier.fromNamespaceAndPath(MOD_ID, "dropped_payload")
        );
        ENTITIES.addAlias(
                Identifier.fromNamespaceAndPath("drone", "drone_operator"),
                Identifier.fromNamespaceAndPath(MOD_ID, "drone_operator")
        );
        ITEMS.addAlias(
                Identifier.fromNamespaceAndPath("drone", "drone_operator_spawn_egg"),
                Identifier.fromNamespaceAndPath(MOD_ID, "drone_operator_spawn_egg")
        );
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> FPV_CONNECT = SOUNDS.register(
            "fpv_connect",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, "fpv_connect"))
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRONE_TAB = CREATIVE_TABS.register(
            "flight_systems",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.fpvdrone"))
                    .icon(() -> DRONE.get().getDefaultInstance())
                    .build()
    );

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MOSQUITO_LIFT_TEST =
            TEST_FUNCTIONS.register("mosquito_lift_and_battery", () -> DroneGameTests::mosquitoLiftAndBattery);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PAYLOAD_RELEASE_TEST =
            TEST_FUNCTIONS.register("payload_release", () -> DroneGameTests::payloadRelease);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FAILSAFE_TEST =
            TEST_FUNCTIONS.register("server_authority_and_failsafe", () -> DroneGameTests::serverAuthorityAndFailsafe);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OPERATOR_DEPLOYMENT_TEST =
            TEST_FUNCTIONS.register("operator_deployment", () -> DroneGameTests::operatorDeployment);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OPERATOR_PURSUIT_TEST =
            TEST_FUNCTIONS.register("operator_lock_and_pursuit", () -> DroneGameTests::operatorLockAndPursuit);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OPERATOR_ATTACK_TEST =
            TEST_FUNCTIONS.register("operator_attack_impact", () -> DroneGameTests::operatorAttackImpact);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OPERATOR_REENGAGE_TEST =
            TEST_FUNCTIONS.register("operator_miss_and_reengage", () -> DroneGameTests::operatorMissAndReengage);

    public DroneMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(DroneNetwork::registerPayloads);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        modEventBus.addListener(this::registerGameTests);
        modEventBus.addListener(this::buildCreativeTab);
        modEventBus.addListener(this::registerTicketControllers);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(DRONE_CHUNK_TICKETS);
        event.register(PILOT_BODY_CHUNK_TICKETS);
    }

    private void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() != DRONE_TAB.get()) return;
        event.accept(DRONE.get());
        event.accept(THERMAL_DRONE.get());
        event.accept(REMOTE_CONTROL.get());
        event.accept(FPV_GOGGLES.get());
        event.accept(BATTERY.get());
        event.accept(RPG_WARHEAD.get());
        event.accept(ANTENNA.get());
        event.accept(CAMERA.get());
        event.accept(THERMAL.get());
        event.accept(ELECTRIC_MOTOR.get());
        event.accept(PROPELLER.get());
        event.accept(BETAFLIGHT.get());
        event.accept(PIVOT_CONTROLLER.get());
        event.accept(BOMB_HOLDER.get());
        event.accept(BOMB_3.get());
        // Preserve the original 26.2 encounter feature alongside the V1.1.4
        // item set so pack makers and operators can still spawn it directly.
        event.accept(DRONE_OPERATOR_SPAWN_EGG.get());
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> flightEnvironment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MOD_ID, "flight"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        Holder<TestEnvironmentDefinition<?>> operatorDeploymentEnvironment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MOD_ID, "operator_deployment"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        Holder<TestEnvironmentDefinition<?>> operatorPursuitEnvironment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MOD_ID, "operator_pursuit"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        Holder<TestEnvironmentDefinition<?>> operatorAttackEnvironment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MOD_ID, "operator_attack"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        Holder<TestEnvironmentDefinition<?>> operatorReengageEnvironment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(MOD_ID, "operator_reengage"),
                new TestEnvironmentDefinition.AllOf(List.of())
        );
        TestData<Holder<TestEnvironmentDefinition<?>>> flightData = testData(flightEnvironment);
        registerTest(event, "mosquito_lift_and_battery", MOSQUITO_LIFT_TEST, flightData);
        registerTest(event, "payload_release", PAYLOAD_RELEASE_TEST, flightData);
        registerTest(event, "server_authority_and_failsafe", FAILSAFE_TEST, flightData);
        // Operator tests create real server players. Use the largest padding
        // accepted by the synced 26.2 GameTest codec so parallel structures
        // remain outside each drone's 96-block acquisition radius.
        registerTest(event, "operator_deployment", OPERATOR_DEPLOYMENT_TEST,
                testData(operatorDeploymentEnvironment, 128));
        // Hunting drones stalk from altitude before diving, so the attack tests
        // need room for a full approach — and the re-engage test for a whole
        // miss, break-off, and second attack run.
        registerTest(event, "operator_lock_and_pursuit", OPERATOR_PURSUIT_TEST,
                testData(operatorPursuitEnvironment, 128, 400));
        registerTest(event, "operator_attack_impact", OPERATOR_ATTACK_TEST,
                testData(operatorAttackEnvironment, 128, 400));
        registerTest(event, "operator_miss_and_reengage", OPERATOR_REENGAGE_TEST,
                testData(operatorReengageEnvironment, 128, 600));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        return testData(environment, 2);
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
            Holder<TestEnvironmentDefinition<?>> environment,
            int padding
    ) {
        return testData(environment, padding, 160);
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
            Holder<TestEnvironmentDefinition<?>> environment,
            int padding,
            int maxTicks
    ) {
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("minecraft", "empty"),
                maxTicks,
                0,
                true,
                Rotation.NONE,
                false,
                1,
                1,
                false,
                padding
        );
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(DRONE_OPERATOR_ENTITY.get(), DroneOperatorEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                DRONE_OPERATOR_ENTITY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DroneOperatorEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    private static void registerTest(
            RegisterGameTestsEvent event,
            String name,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function,
            TestData<Holder<TestEnvironmentDefinition<?>>> data
    ) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(MOD_ID, name),
                new FunctionGameTestInstance(function.getKey(), data)
        );
    }
}
