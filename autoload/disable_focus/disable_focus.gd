extends Node


func _enter_tree() -> void :
    get_tree().node_added.connect(func(node):
        if node is Control:
            node.focus_mode = Control.FOCUS_NONE
    )
