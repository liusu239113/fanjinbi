class_name TweenHelper

static  var tweens_dict: Dictionary = {}

static func cleanup():
    for key in tweens_dict.keys():
        if tweens_dict.has(key) and not (tweens_dict[key] as Tween).is_running():
            (tweens_dict[key] as Tween).kill()
            tweens_dict.erase(key)

static func tween(reference_name: String, origin_node: Node) -> Tween:
    var key = str(origin_node.get_instance_id()) + "_" + reference_name

    if tweens_dict.has(key) and (tweens_dict[key] as Tween).is_running():
        (tweens_dict[key] as Tween).kill()
    tweens_dict[key] = origin_node.create_tween()
    return tweens_dict[key]
