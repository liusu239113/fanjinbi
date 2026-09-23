extends Area2D
class_name Item

static  var collision_distance_diameter_squared = pow(32, 2) * 2
@export var can_be_pushed: = true
var push_force: = Vector2.ZERO
var push_timer: = Timer.new()
var screen_limit = Vector2(260, 170)
var screen_limit_offset = Vector2(0, -20)
var should_push: = true
var id: String
static  var item_ids = 0
static  var available_items = {}

func _ready():
    id = str(item_ids)
    item_ids += 1
    push_timer.one_shot = false
    push_timer.wait_time = 0.1
    push_timer.timeout.connect(push_neighbours_away)
    add_child(push_timer)
    push_timer.start()
    push_neighbours_away()
    collision_distance_diameter_squared = pow(32, 2) * 2

func _physics_process(delta: float) -> void :
    if can_be_pushed: global_position += push_force * delta
    position = position.clamp( - screen_limit + screen_limit_offset, screen_limit + screen_limit_offset)



func push_neighbours_away():
    push_force = Vector2.ZERO
    if not collision_distance_diameter_squared: return
    if not should_push: return
    var max_pushes = 0
    for neighbour in available_items.values() as Array[Item]:
        if max_pushes > 12: break
        if neighbour.global_position.distance_squared_to(global_position) > collision_distance_diameter_squared: continue
        var direction = global_position - neighbour.global_position
        var distance = direction.length()
        var min_dist = 15.0
        if distance == 0:
            var random_offset = Vector2(randf() - 0.5, randf() - 0.5).normalized() * 0.1
            push_force += random_offset
            if "push_force" in neighbour:
                neighbour.push_force -= random_offset
        elif distance < min_dist:
            var push_strength_away = 15.0
            var overlap = min_dist - distance
            var push_vector = direction.normalized() * overlap * push_strength_away

            push_force += push_vector
            if "push_force" in neighbour:
                neighbour.push_force -= push_vector
        max_pushes += 1
    const MAX_PUSH_FORCE = 20.0
    push_force.x = clamp(push_force.x, - MAX_PUSH_FORCE, MAX_PUSH_FORCE)
    push_force.y = clamp(push_force.y, - MAX_PUSH_FORCE, MAX_PUSH_FORCE)
