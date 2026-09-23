extends Button

func _ready() -> void :
    visible = false
    text = tr("SACRIFICE") + " " + BigHelper.convert_big_to_text(StatsContext.prestige_points)


func _on_timer_timeout() -> void :
    visible = false
    text = tr("SACRIFICE") + " " + BigHelper.convert_big_to_text(StatsContext.prestige_points)


func _on_pressed() -> void :
    TransitionHandler.transition_to(load("res://ui/skill_tree/skill_tree.tscn"), 1)
