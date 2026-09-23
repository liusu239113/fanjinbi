extends Node

@export var packed_scene: PackedScene
@export var pool_size: = 1000
var pool: = []
var available_index: = 0

func create_pool():
    pool.resize(pool_size)
    for i in range(pool_size):
        var object = packed_scene.instantiate()
        pool[i] = object
        get_tree().current_scene.add_child(object)

        object.hide()
    available_index = pool_size - 1

func get_from_pool() -> Node:
    var object: Node
    if available_index < 0:
        print("Pool was overexceeded!")
        object = packed_scene.instantiate()
        get_tree().current_scene.add_child(object)
    else:
        object = pool[available_index]
        available_index -= 1



    object.show()
    return object

func add_back_to_pool(object: Node) -> void :
    available_index += 1
    if available_index >= pool_size:
        available_index = pool_size - 1
        return

    pool[available_index] = object

    object.hide()
