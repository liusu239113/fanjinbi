extends Interactable
class_name Dice

@onready var mesh: = $SubViewport / Node3D / MeshInstance3D
@onready var dice_sprite: = $DiceSprite
@onready var shake_sound: = $ShakeSound
@onready var land_sound: = $LandSound
@onready var jump_start_sound: = $JumpStartSound


@export var throw_strength: = 150.0
@export var rotation_speed_multiplier: = 1.0
@export var min_roll_duration: float = 1.0
@export var max_roll_duration: float = 1.5
var is_rolling: bool = false

var roll_timer: float = 0.0

var roll_face: int = 1
var velocity: Vector2 = Vector2.ZERO
var rotation_speed: Vector3 = Vector3.ZERO



var face_orientations: = {
    1: Vector3(0, 0, 0), 
    2: Vector3(PI / 2, 0, 0), 
    3: Vector3(PI, 0, 0), 
    4: Vector3( - PI / 2, 0, 0), 
    5: Vector3(0, PI / 2, 0), 
    6: Vector3(0, - PI / 2, 0)
}

func _physics_process(delta: float) -> void :
    super (delta)
    if not is_rolling: return
    position += velocity * delta
    mesh.rotation_degrees += rotation_speed * delta * rotation_speed_multiplier

    roll_timer -= delta
    if roll_timer <= 0:
        stop_roll_action()


func _on_mouse_clicked() -> void :

    if not is_rolling:
        start_roll_action()


func start_roll_action() -> void :
    if is_rolling: return


    is_rolling = true
    roll_face = get_face_to_roll_for()
    roll_timer = randf_range(min_roll_duration, max_roll_duration)
    var jump_tween = TweenHelper.tween("jump", self)
    get_tree().create_timer(roll_timer - roll_timer / 7.0).timeout.connect(land_sound.play)
    jump_tween.tween_property(dice_sprite, "position", Vector2(0, -50), (roll_timer / 5.0)).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_LINEAR)
    jump_tween.tween_property(dice_sprite, "position", Vector2(0, 0), (roll_timer / 5.0)).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT).set_delay((roll_timer / 5.0) * 3)

    var direction = Vector2(randf() - 0.5, randf() - 0.5).normalized()
    velocity = direction * throw_strength


    mesh.rotation = Vector3.ZERO
    rotation_speed = Vector3(
        velocity.y * 5.0, 
        velocity.x * 5.0, 
        0
    )

func get_face_to_roll_for() -> int:
    return randi_range(1, 6)

func stop_roll_action() -> void :
    is_rolling = false
    velocity = Vector2.ZERO
    rotation_speed = Vector3.ZERO

    var final_angles: Vector3 = face_orientations[roll_face]
    mesh.rotation = final_angles
    value_gained.emit(roll_face * value)
    StatsContext.add_dice_to_combination(roll_face)
