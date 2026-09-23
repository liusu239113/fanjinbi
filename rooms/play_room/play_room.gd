extends Node2D
class_name PlayRoom

@export var table_sprites: Dictionary[String, Texture2D]
@onready var table_sprite: = % Table

@export var table_sprite_key: = "normal":
    set(value):
        table_sprite_key = value
        if not table_sprites.has(table_sprite_key): return
        table_sprite.texture = table_sprites[table_sprite_key]

@onready var money_label: = $UI / CenterContainer / HSplitContainer / Money
@onready var dice_scene: = preload("res://items/interactables/dice/dice.tscn")
@onready var small_coin_scene: = preload("res://items/interactables/coin/small_coin/small_coin.tscn")
@onready var medium_coin_scene: = preload("res://items/interactables/coin/medium_coin/medium_coin.tscn")
@onready var large_coin_scene: = preload("res://items/interactables/coin/large_coin/large_coin.tscn")
@onready var helper_scene: = preload("res://items/helper/helper.tscn")
@onready var interactables_container: = % Interactables

var available_helper_targets: Array[Interactable] = []

var small_coins: Array[Coin] = []
var medium_coins: Array[Coin] = []
var large_coins: Array[Coin] = []
var helpers: Array[Helper] = []
var die: Array[Dice] = []

const MONEY_STRING_FORMAT: = "[center][wave]{amount}"

func connect_interactable(interactable: Interactable):
    interactable.value_gained.connect(func(value: Big, _source: String):
        StatsContext.money = Big.add(StatsContext.money, value)
    )
    update_available_helpers_targets()

func update_available_helpers_targets():
    available_helper_targets.clear()
    available_helper_targets.append_array(small_coins)
    if StatsContext.helper_can_trigger_medium_coins: available_helper_targets.append_array(medium_coins)
    if StatsContext.helper_can_trigger_large_coins: available_helper_targets.append_array(large_coins)
    if StatsContext.helper_can_trigger_die: available_helper_targets.append_array(die)

func update_money_label():
    money_label.text = MONEY_STRING_FORMAT.replace("{amount}", BigHelper.convert_big_to_text(StatsContext.money))

func _ready():
    AudioManager.play_bgm("bgm_main")
    AutomationContext.register_automated_clicker(self)
    ResourceLoader.load("res://items/interactables/dice/dice_texture_material.tres")
    FloatingTextObjectPool.create_pool()
    update_money_label()
    for i in range(StatsContext.small_coins):
        spawn_coin("small")
    for i in range(StatsContext.medium_coins):
        spawn_coin("medium")
    for i in range(StatsContext.large_coins):
        spawn_coin("large")
    for i in range(StatsContext.die):
        spawn_dice()
    for i in range(StatsContext.helpers):
        spawn_helper()

    var update_money_tween = TweenHelper.tween("update_money_display", self)
    update_money_tween.set_loops(-1)
    update_money_tween.tween_callback(update_money_label).set_delay(0.2)

    var spawn_small_coin_callable = Callable(func(amount):
        while amount > small_coins.size():
            spawn_coin("small")
    )
    StatsContext.small_coins_changed.connect(spawn_small_coin_callable)

    var spawn_medium_coin_callable = Callable(func(amount):
        while amount > medium_coins.size():
            spawn_coin("medium")
    )
    StatsContext.medium_coins_changed.connect(spawn_medium_coin_callable)


    var spawn_large_coin_callable = Callable(func(amount):
        while amount > large_coins.size():
            spawn_coin("large")
    )
    StatsContext.large_coins_changed.connect(spawn_large_coin_callable)

    var spawn_die_callable = Callable((func(amount):
        while amount > die.size():
            spawn_dice()
    ))
    StatsContext.die_changed.connect(spawn_die_callable)

    var spawn_helper_callable = Callable(func(amount):
        while amount > helpers.size():
            spawn_helper()
    )
    StatsContext.helpers_changed.connect(spawn_helper_callable)

    tree_exiting.connect(func():
        StatsContext.small_coins_changed.disconnect(spawn_small_coin_callable)
        StatsContext.medium_coins_changed.disconnect(spawn_medium_coin_callable)
        StatsContext.large_coins_changed.disconnect(spawn_large_coin_callable)
        StatsContext.die_changed.disconnect(spawn_die_callable)
        StatsContext.helpers_changed.disconnect(spawn_helper_callable)
    , CONNECT_ONE_SHOT)
    update_available_helpers_targets()

func spawn_dice():
    var dice = dice_scene.instantiate()
    die.push_back(dice)
    interactables_container.add_child(dice)
    connect_interactable(dice)

func spawn_helper():
    var helper: Helper = helper_scene.instantiate()
    helpers.push_back(helper)
    interactables_container.add_child(helper)
    helper.available_targets = available_helper_targets

func spawn_coin(type: StringName):
    var coin: Coin
    match type:
        "small":
            coin = small_coin_scene.instantiate()
            small_coins.push_back(coin)
        "medium":
            coin = medium_coin_scene.instantiate()
            if medium_coins.size() <= 0:
                % CoinBackground.current_mode = % CoinBackground.COIN_MODE.MEDIUM
            medium_coins.push_back(coin)
        "large":
            coin = large_coin_scene.instantiate()
            if large_coins.size() <= 0:
                % CoinBackground.current_mode = % CoinBackground.COIN_MODE.LARGE
            large_coins.push_back(coin)

    interactables_container.add_child(coin)
    connect_interactable(coin)

func _on_shop_sold_something() -> void :
    update_available_helpers_targets()

func _on_options_button_pressed() -> void :
    OptionsMenu.visible = !OptionsMenu.visible
