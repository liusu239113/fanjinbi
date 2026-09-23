extends Item
class_name Interactable

@export var value_key: = "NO_KEY"
@export var key: = "NO_KEY"
var value: int = 0
var big_value: Big

var _is_hovered = false
signal value_gained(value: Big, source: String)
signal mouse_clicked
signal mouse_hovered

func _ready():
    super ()
    if not StatsContext.money_earned_by_interactable.has(key):
            var new_value_earned: = {
                "total": Big.new(0), 
                "sources": {
                }
            }
            StatsContext.money_earned_by_interactable[key] = new_value_earned
    value = GameValuesContext.get_value_by_full_key(value_key)
    big_value = Big.new(GameValuesContext.get_value_by_full_key(value_key))
    connect("value_gained", _on_value_gained)


func _physics_process(delta):
    super (delta)
    if not collision_distance_diameter_squared: return
    var _was_hovered = _is_hovered
    _is_hovered = global_position.distance_squared_to(get_global_mouse_position()) < collision_distance_diameter_squared
    if _is_hovered:
        if Input.is_action_just_pressed("trigger_coin") and not OptionsMenu.visible:
            mouse_clicked.emit()
        if _was_hovered == _is_hovered: return
        mouse_hovered.emit()



func _on_value_gained(given_value: Big, source: String):
    if given_value.isGreaterThan(0):
        var value_earned = StatsContext.money_earned_by_interactable[key]

        value_earned.total = Big.add(value_earned.total, given_value)
        if value_earned.sources.has(source):
            value_earned.sources[source] = Big.add(value_earned.sources[source], given_value)
        else:
            value_earned.sources[source] = given_value

        if given_value.isGreaterThan(StatsContext.highest_value_gained):
            StatsContext.highest_value_gained = given_value

        if AutomationContext.IS_ACTIVE: return
        var size_factor = 1.0
        if StatsContext.highest_value_gained.isGreaterThan(0): size_factor = given_value.dividedBy(StatsContext.highest_value_gained).toFloat()
        reset_physics_interpolation()
        FloatingText.spawn_float_text(
            global_position, 
            BigHelper.convert_big_to_text(given_value) + " " + BBCodeHelper.build(tr("CURRENCY")).color(Color.ORANGE.to_html()).result(), 
            clamp(size_factor, 0.75, 2.5)
            )
