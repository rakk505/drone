package com.modernity.drone;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneOperatorEntity;
import com.modernity.drone.entity.DroppedPayloadEntity;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.item.DroneItem;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(DroneMod.MOD_ID)
public final class DroneMod {
    public static final String MOD_ID = "drone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    public static final DeferredItem<DroneItem> MOSQUITO_DRONE = ITEMS.registerItem(
            "drone",
            properties -> new DroneItem(properties, DroneKind.MOSQUITO),
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<DroneItem> PAYLOAD_DRONE = ITEMS.registerItem(
            "mavic_drone",
            properties -> new DroneItem(properties, DroneKind.PAYLOAD),
            properties -> properties.stacksTo(1)
    );
    public static final DeferredItem<Item> FPV_BATTERY = ITEMS.registerSimpleItem("battery", properties -> properties.durability(1000));
    public static final DeferredItem<Item> DJI_BATTERY = ITEMS.registerSimpleItem("battery_dji", properties -> properties.durability(1000));
    public static final DeferredItem<Item> FPV_CONTROLLER = ITEMS.registerSimpleItem("remote_control", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> DJI_CONTROLLER = ITEMS.registerSimpleItem("dji_controller", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> RPG_WARHEAD = ITEMS.registerSimpleItem("rpg7", properties -> properties.stacksTo(4));
    public static final DeferredItem<Item> FORTY_MM_PAYLOAD = ITEMS.registerSimpleItem("40mm_explosive", properties -> properties.stacksTo(16));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE_ENTITY = ENTITIES.registerEntityType(
            "drone",
            DroneEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.85F, 0.55F)
                    .eyeHeight(0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<DroppedPayloadEntity>> DROPPED_PAYLOAD_ENTITY =
            ENTITIES.registerEntityType(
                    "dropped_payload",
                    DroppedPayloadEntity::new,
                    MobCategory.MISC,
                    builder -> builder.sized(0.24F, 0.42F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRONE_TAB = CREATIVE_TABS.register(
            "flight_systems",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.drone"))
                    .icon(() -> MOSQUITO_DRONE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(MOSQUITO_DRONE.get());
                        output.accept(PAYLOAD_DRONE.get());
                        output.accept(FPV_CONTROLLER.get());
                        output.accept(DJI_CONTROLLER.get());
                        output.accept(FPV_BATTERY.get());
                        output.accept(DJI_BATTERY.get());
                        output.accept(RPG_WARHEAD.get());
                        output.accept(FORTY_MM_PAYLOAD.get());
                        output.accept(DRONE_OPERATOR_SPAWN_EGG.get());
                    })
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

    public DroneMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(DroneNetwork::registerPayloads);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        modEventBus.addListener(this::registerGameTests);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
        TestData<Holder<TestEnvironmentDefinition<?>>> flightData = testData(flightEnvironment);
        registerTest(event, "mosquito_lift_and_battery", MOSQUITO_LIFT_TEST, flightData);
        registerTest(event, "payload_release", PAYLOAD_RELEASE_TEST, flightData);
        registerTest(event, "server_authority_and_failsafe", FAILSAFE_TEST, flightData);
        registerTest(event, "operator_deployment", OPERATOR_DEPLOYMENT_TEST, testData(operatorDeploymentEnvironment));
        registerTest(event, "operator_lock_and_pursuit", OPERATOR_PURSUIT_TEST, testData(operatorPursuitEnvironment));
        registerTest(event, "operator_attack_impact", OPERATOR_ATTACK_TEST, testData(operatorAttackEnvironment));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("minecraft", "empty"),
                160,
                0,
                true,
                Rotation.NONE,
                false,
                1,
                1,
                false,
                2
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
