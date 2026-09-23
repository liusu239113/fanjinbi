extends Interactable
class_name Helper

enum STATE{
    IDLING, 
    MOVING_TO_TARGET, 
    INTERACTING, 
    SLEEPING
}

@onready var animated_sprite: = $AnimatedSprite2D
@export var move_speed: = 100.0
@export var minimal_idle_time: = 3.0
@export var maximum_idle_time: = 5.0
@export var base_sleep_chance: = 0.01
@export var minimal_sleep_time: = 10.0
@export var maximum_sleep_time: = 30.0
@export var available_targets: Array[Interactable] = []
var needed_minimal_distance: = 15.0
var current_target: Interactable
var state: STATE:
    set(value):
        state = value
        match (state):
            STATE.IDLING: animated_sprite.play("idle_" + vertical_orientation)
            STATE.MOVING_TO_TARGET: animated_sprite.play("walk_side_" + vertical_orientation)
            STATE.INTERACTING: animated_sprite.play("throw_" + vertical_orientation)
            STATE.SLEEPING: animated_sprite.play("sleep")
        state_time_current = 0.0
var state_time_current: = 0.0
var state_time: float
var vertical_orientation: = "up"

func assign_target_interactable():
    if not available_targets.is_empty():
        current_target = available_targets.pick_random()


func _ready() -> void :

    state = STATE.IDLING


func _physics_process(delta: float) -> void :
    super (delta)
    match state:
        STATE.IDLING: idle_state(delta)
        STATE.MOVING_TO_TARGET: move_to_target_state(delta)
        STATE.SLEEPING: sleep_state(delta)

func idle_state(delta):
    state_time_current += delta
    if state_time_current > state_time:
        state_time_current = 0
        assign_target_interactable()
        set_orientation()
        state = STATE.MOVING_TO_TARGET

func sleep_state(delta):
    state_time_current += delta
    if state_time_current > state_time:
        state_time_current = 0
        assign_target_interactable()
        set_orientation()
        state = STATE.MOVING_TO_TARGET

func set_orientation():
    if current_target:
        var v_diff = current_target.global_position.y - global_position.y
        var h_diff = current_target.global_position.x - global_position.x
        if v_diff != 0: vertical_orientation = "up" if v_diff < 0 else "down"
        if h_diff != 0: animated_sprite.flip_h = h_diff < 0

func move_to_target_state(delta):
    if not current_target:
        state = STATE.IDLING
        return
    global_position = global_position.move_toward(current_target.global_position, delta * move_speed * StatsContext.helper_efficiency_multiplier)
    set_orientation()
    if global_position.distance_to(current_target.global_position) <= needed_minimal_distance:
        state = STATE.INTERACTING


func _on_animation_finished() -> void :
    if (animated_sprite.animation as String).begins_with("throw_"):
        if current_target is Coin:
            current_target.start_flip_action(Coin.SOURCE.HELPER)
        else:
            current_target.mouse_clicked.emit()

        if randf() <= base_sleep_chance:
            state = STATE.SLEEPING
            state_time = randf_range(minimal_sleep_time, maximum_sleep_time) / StatsContext.helper_efficiency_multiplier
        else:
            state = STATE.IDLING
            state_time = randf_range(minimal_idle_time, maximum_idle_time) / StatsContext.helper_efficiency_multiplier


func _handle_click() -> void :
    if state == STATE.SLEEPING:
        state_time_current = state_time + 1
