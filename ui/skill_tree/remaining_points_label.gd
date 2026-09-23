extends RichTextLabel

func _ready() -> void :
    update_text()
    StatsContext.prestige_points_changed.connect(func(_points: Big):
        update_text()
    )
func update_text():
    text = "[wave freq=3 amp=7]" + tr("REMAINING_POINTS").replace("{points}", BigHelper.convert_big_to_text(StatsContext.prestige_points))
