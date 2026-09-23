extends GraphElement
class_name SkillTreeConnection

@export var texture_invalid: Texture2D
@export var texture_valid: Texture2D
@onready var line: Line2D = $Line2D
@export var id: int
@export var from: SkillTreeNode
@export var to: SkillTreeNode
@export var is_active: bool = false:
    set(value):
        is_active = value
        line.texture = texture_invalid if not is_active else texture_valid

func _ready() -> void :
    is_active = false
    if from and to:
        position_offset = from.position_offset
        line.set_point_position(1, to.position_offset - from.position_offset)

func validate():
    is_active = to.is_active and from.is_active
