extends Interactable
class_name Coin

enum SOURCE{
    HELPER, 
    REFLIP, 
    SHOCKWAVE, 
    MANUAL
}

enum COIN_TYPE{
    SMALL_COIN, 
    MEDIUM_COIN, 
    LARGE_COIN
}

var group_to_bounce: StringName = ""
static  var available_coins = {}
var group_to_bounce_dict: Dictionary[COIN_TYPE, StringName] = {
    COIN_TYPE.SMALL_COIN: "", 
    COIN_TYPE.MEDIUM_COIN: "small_coins", 
    COIN_TYPE.LARGE_COIN: "medium_and_small_coins"
}

@onready var sprite_head: = $CoinSpriteContainer / SpriteHead
@onready var sprite_tails: = $CoinSpriteContainer / SpriteTails
@onready var flip_animation: = $CoinSpriteContainer / FlipAnimation
@onready var coin_sprite_container: = $CoinSpriteContainer
@onready var shadow_sprite: = $ShadowSprite

var is_falling: = false
var jump_height: = 0.0:
    set(value):
        if value < jump_height and value < 50.0 and is_falling:
            AudioManager.play_sound_effect("flip_end_sound")
            AudioManager.play_sound_effect("jump_end_sound")
            is_falling = false
        jump_height = value
        coin_sprite_container.position.y = - jump_height
        shadow_sprite.scale = (Vector2.ONE - Vector2.ONE * (jump_height / max_jump_distance) / 2.0) * shadow_default_scale
var has_flip_started: = false
var is_in_air: = false
var coin_rotation: = 0.0
var current_upwards_force: = 0.0
var move_force: = Vector2.ZERO
@export var flip_duration_min: = 1.8
@export var flip_duration_max: = 1.4
@export var min_jump_distance: = 100.0
@export var max_jump_distance: = 160.0
@export var min_move_force: = Vector2(1.0, 1.0)
@export var max_move_force: = Vector2(2.0, 2.0)
@export var type: COIN_TYPE = COIN_TYPE.SMALL_COIN
var shadow_default_scale: Vector2
var is_tails: = false
var is_spawning_in: = false
const BASE_SPEED: = 50.0
var source: SOURCE

func _ready():
    super ()
    position += Vector2.from_angle(deg_to_rad(359 * randf())) * (50 * randf())
    spawn_in_animation()


    group_to_bounce = group_to_bounce_dict.get(type, "")
    shadow_default_scale = shadow_sprite.scale
    _register_idle_coin()

func _exit_tree() -> void :
    _unregister_idle_coin()

func spawn_in_animation():
    coin_sprite_container.position.y = -400
    is_spawning_in = true
    reset_physics_interpolation()
    var fall_tween = TweenHelper.tween("fall", self)
    fall_tween.tween_property(coin_sprite_container, "position", Vector2(coin_sprite_container.position.x, 0), 0.3).set_ease(Tween.EASE_OUT)
    fall_tween.tween_callback(func():
        AudioManager.play_sound_effect("flip_end_sound")
        AudioManager.play_sound_effect("jump_end_sound")
    )
    fall_tween.tween_property(coin_sprite_container, "position", Vector2(coin_sprite_container.position.x, -5), 0.1).set_ease(Tween.EASE_OUT)
    fall_tween.tween_property(coin_sprite_container, "position", Vector2(coin_sprite_container.position.x, 0), 0.2).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_ELASTIC)
    fall_tween.tween_callback(func(): is_spawning_in = false)

func _on_mouse_clicked():
    if not has_flip_started:
        start_flip_action(SOURCE.MANUAL)

func _on_mouse_hovered():
    if type == COIN_TYPE.SMALL_COIN and StatsContext.trigger_small_coins_on_mouse_over:
        if not has_flip_started:
            start_flip_action(SOURCE.MANUAL)

func start_flip_action(_source: SOURCE):
    if is_spawning_in: return
    if has_flip_started: return
    _unregister_idle_coin()
    source = _source
    should_push = true
    if not check_sound_cap():
        AudioManager.play_sound_effect("jump_start_sound")
    AudioManager.play_sound_effect("flip_start_sound")
    sprite_head.hide()
    sprite_tails.hide()
    flip_animation.show()
    has_flip_started = true
    is_tails = [false, true].pick_random()
    if source == SOURCE.MANUAL:
        move_force = Vector2(randf_range(min_move_force.x, max_move_force.x), randf_range(min_move_force.y, max_move_force.y)) * (global_position - get_global_mouse_position()).normalized()
    else:
        if get_parent().global_position.distance_to(global_position) < 250 and randf() > 0.33:
            move_force = Vector2(randf_range(min_move_force.x, max_move_force.x) * [1.0, -1.0].pick_random(), randf_range(min_move_force.y, max_move_force.y) * [1.0, -1.0].pick_random())
        else:
            move_force = position.direction_to(Vector2.ZERO) * Vector2(randf_range(min_move_force.x, max_move_force.x), randf_range(min_move_force.y, max_move_force.y))
    var flip_duration = randf_range(flip_duration_min, flip_duration_max) / StatsContext[get_type_as_string() + "_coin_flip_speed_multiplier"]
    get_tree().create_timer(flip_duration - flip_duration / 2.13).timeout.connect(on_coin_flip_end)
    var move_force_tween = TweenHelper.tween("move_force", self)
    move_force_tween.tween_property(self, "move_force", Vector2.ZERO, flip_duration / 1.5).set_trans(Tween.TRANS_CIRC)


    var coin_jump_tween = TweenHelper.tween("coin_jump", self)
    coin_jump_tween.tween_property(self, "jump_height", randf_range(min_jump_distance, max_jump_distance), flip_duration / 4.0).set_trans(Tween.TRANS_CIRC)
    coin_jump_tween.tween_callback(func(): is_falling = true)
    coin_jump_tween.tween_property(self, "jump_height", 0.0, flip_duration / 2.0).set_trans(Tween.TRANS_ELASTIC)

func on_coin_flip_end():
    flip_animation.hide()
    has_flip_started = false
    should_push = true
    _register_idle_coin()

    if is_tails:
        value_gained.emit(
                Big.add(big_value, StatsContext[get_type_as_string() + "_coin_additional_coin_value"])
                .times(Big.new(StatsContext[get_type_as_string() + "_additional_coin_multiplier"])), 
                SOURCE.keys()[source]
            )
    else:
        if not check_sound_cap(): AudioManager.play_sound_effect("flip_fail_sound")

    if StatsContext.large_coins_trigger_coins_on_land:
        var arr: Array = get_tree().get_nodes_in_group(group_to_bounce).filter(func(e: Coin): return global_position.distance_squared_to(e.global_position) <= (collision_distance_diameter_squared))
        arr.slice(0, min(8, arr.size())).all(func(e):
            e.start_flip_action(SOURCE.SHOCKWAVE)
            return true
        )

    sprite_head.visible = !is_tails
    sprite_tails.visible = is_tails
    if randf() < StatsContext.coins_reflip_chance:
        start_flip_action(SOURCE.REFLIP)

func get_type_as_string():
    match type:
        COIN_TYPE.SMALL_COIN: return "small"
        COIN_TYPE.MEDIUM_COIN: return "medium"
        COIN_TYPE.LARGE_COIN: return "large"

func check_sound_cap(limit = 10):
    return (StatsContext.small_coins + StatsContext.medium_coins + StatsContext.large_coins) - available_coins.size() > limit

func _physics_process(delta):
    super (delta)
    global_position += move_force * delta * BASE_SPEED


func _register_idle_coin():
    available_items[id] = self
    available_coins[id] = self

func _unregister_idle_coin():
    if available_items.has(id):
        available_items.erase(id)
    if available_coins.has(id):
        available_coins.erase(id)
