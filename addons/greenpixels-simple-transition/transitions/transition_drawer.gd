class_name TransitionDrawer
extends Control

var progress: float = 0.0
var transition: BaseTransition = null

func _ready() -> void :
    process_mode = Node.PROCESS_MODE_ALWAYS
    mouse_filter = Control.MOUSE_FILTER_IGNORE
    top_level = true

func _draw() -> void :
    if transition:
        transition.on_transition(self, progress)

static func draw_full_rect(drawer: Control, viewport_size: Vector2):
    var rect_points = PackedVector2Array([
            Vector2.ZERO, 
            Vector2(viewport_size.x, 0), 
            viewport_size, 
            Vector2(0, viewport_size.y)
        ])
    drawer.draw_colored_polygon(rect_points, Color(0, 0, 0, 1))

func _reset() -> void :
    progress = 0.0
    transition = null
    queue_redraw()
