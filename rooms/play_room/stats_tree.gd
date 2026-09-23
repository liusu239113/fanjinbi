extends Tree

var total_root: TreeItem = null
var money_sources_root: TreeItem = null
var finances_root: TreeItem = null
var current_roots = {}

func _ready() -> void :
    total_root = create_item()
    total_root.set_text(0, "STATS_OVERVIEW")

    finances_root = total_root.create_child()
    finances_root.set_text(0, "STATS_FINANCES")

    money_sources_root = total_root.create_child()
    money_sources_root.set_text(0, "STATS_MONEY_SOURCES")

func _on_stats_tree_update_timer_timeout() -> void :
    _create_or_update_root("STATS_TOTAL_MONEY", StatsContext.total_money, finances_root)
    _create_or_update_root("STATS_HIGHEST_MONEY", StatsContext.highest_money, finances_root)
    for key in StatsContext.money_earned_by_interactable.keys():
        var earned_information = StatsContext.money_earned_by_interactable[key]
        if not key in current_roots:
            var root = money_sources_root.create_child()
            root.set_text(0, "STATS_" + key)
            root.set_text(1, BigHelper.convert_big_to_text(earned_information.total))
            current_roots[key] = {"control": root, "sources": {}}
        else:
            current_roots[key].control.set_text(0, "STATS_" + key)
            current_roots[key].control.set_text(1, BigHelper.convert_big_to_text(earned_information.total))

        for earn_key in earned_information.sources.keys():
            var root: TreeItem = current_roots[key].control
            if not earn_key in current_roots[key].sources:
                var source = root.create_child()
                source.set_text(0, "STATS_SOURCE_" + earn_key)
                source.set_text(1, BigHelper.convert_big_to_text(earned_information.sources[earn_key]))
                current_roots[key].sources[earn_key] = source
            else:
                var value = BigHelper.convert_big_to_text(earned_information.sources[earn_key])
                current_roots[key].sources[earn_key].set_text(0, "STATS_SOURCE_" + earn_key)
                current_roots[key].sources[earn_key].set_text(1, value)

func _create_or_update_root(key: String, value: Big, parent: TreeItem):
    var root
    if not key in current_roots:
        root = parent.create_child()
    else:
        root = current_roots[key].control
    root.set_text(0, key)
    root.set_text(1, BigHelper.convert_big_to_text(value))
    current_roots[key] = {"control": root, "sources": {}}
