extends GraphElement
class_name SkillTreeNode

@export var id: int
@export var skill_key: String
@export var max_points: int
@export var needed_neighbour_points: int
@export var connections: Array[SkillTreeConnection] = []
@export var current_points_invested: int = 0
@export var is_active: bool = false:
    set(value):
        is_active = value
        if not is_node_ready():
            await ready
@export var is_root: bool = false
@export var tree: SkillTree

@onready var skill_image: TextureRect = % SkillImage
@onready var border_enabled: TextureRect = % SkillBorderEnabled
@onready var border_disabled: TextureRect = % SkillBorderDisabled
@onready var hover_container: Control = % HoverContainer
@onready var click_container: Control = % ClickContainer
@onready var button: Button = % Button

func _show_description():
    TooltipOverlay.describe(self, "{current} of {max} Skill Points\n\nNEEDS AT LEAST {needed} SURROUNDING POINTS".format({"current": current_points_invested, "max": max_points, "needed": needed_neighbour_points}))

func _on_button_mouse_entered() -> void :
    _show_description()
    if not is_active: return
    var hover_tween = TweenHelper.tween("hover", hover_container).parallel()
    hover_tween.set_parallel()
    hover_tween.tween_property(hover_container, "rotation_degrees", 9, 0.33).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_ELASTIC)
    hover_tween.tween_property(hover_container, "scale", Vector2(1.2, 1.2), 0.33).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_ELASTIC)

func _on_button_mouse_exited() -> void :
    var hover_tween = TweenHelper.tween("hover", hover_container)
    hover_tween.set_parallel()
    hover_tween.tween_property(hover_container, "rotation", rad_to_deg(0), 0.33).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_ELASTIC)
    hover_tween.tween_property(hover_container, "scale", Vector2.ONE, 0.33).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_ELASTIC)

func validate():
    var _is_valid = false
    var neighbour_points = 0
    var already_connected_to: Dictionary[int, SkillTreeNode] = {}
    for connection in connections:
        var neighbour = connection.to if connection.to != self else connection.from
        if neighbour == self: continue
        if neighbour.id in already_connected_to: continue
        neighbour_points += neighbour.current_points_invested
        already_connected_to[neighbour.id] = neighbour
    if connections.size() > 0 and neighbour_points >= needed_neighbour_points and neighbour_points >= 1:
        _is_valid = true
    elif connections.size() == 0:
        _is_valid = true
    elif is_root:
        _is_valid = true
    is_active = _is_valid

    % CurrentPointsLabel.text = str(current_points_invested)
    hover_container.modulate = Color.WHITE if current_points_invested > 0 or is_root else Color.from_hsv(0, 0, 0.3)

    % MaxPointsLabel.text = str(max_points)
    border_disabled.visible = !is_active
    border_enabled.visible = is_active

func _on_button_pressed() -> void :
    if not StatsContext.prestige_points.isGreaterThan(0): return
    tree.pan_to_node(self)
    if not is_active: return
    if current_points_invested >= max_points: return
    current_points_invested += 1
    var hover_tween = TweenHelper.tween("clicked", click_container)
    hover_tween.set_parallel()
    hover_tween.tween_property(click_container, "scale", Vector2.ONE * 1.2, 0.05).set_ease(Tween.EASE_OUT)
    hover_tween.tween_property(click_container, "scale", Vector2.ONE, 0.33).set_ease(Tween.EASE_OUT).set_delay(0.05).set_trans(Tween.TRANS_ELASTIC)
    tree.validate_all()
    _show_description()
    _on_invest()

func _on_invest():
    StatsContext.prestige_points = StatsContext.prestige_points.minus(Big.new(1))
