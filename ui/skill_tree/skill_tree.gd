extends GraphEdit
class_name SkillTree

var skill_tree_node_scene = preload("res://ui/skill_tree/skill_tree_node/skill_tree_node.tscn")
var skill_tree_connection_scene = preload("res://ui/skill_tree/skill_tree_connection/skill_tree_connection.tscn")
const SKILL_TREE_JSON_PATH = "res://ui/skill_tree/skill-tree-data.gp.json"

@export var image_data: Dictionary[String, Texture2D]

@onready var remaining_points_label: RichTextLabel = % RemainingPointsLabel

var skill_nodes: Dictionary[int, SkillTreeNode] = {}
var skill_connections: Dictionary[int, SkillTreeConnection] = {}

func _ready() -> void :
    AudioManager.play_bgm("bgm_skill_tree")
    _generate_skill_tree_from_file()
    validate_all()

func validate_all():
    for node in skill_nodes.values() as Array[SkillTreeNode]:
        node.validate()
    for connection in skill_connections.values():
        connection.validate()

func _generate_skill_tree_from_file():
    if not FileAccess.file_exists(SKILL_TREE_JSON_PATH):
        push_error("Unable to find skill tree file under " + SKILL_TREE_JSON_PATH)
        return

    var json_file = FileAccess.open(SKILL_TREE_JSON_PATH, FileAccess.READ)
    var json_text = json_file.get_as_text()
    var json = JSON.parse_string(json_text)

    if not json:
        push_error("Failed to parse skill tree JSON")
        return


    for node_data in json.nodes:
        var node = skill_tree_node_scene.instantiate()
        node.position_offset = Vector2(node_data.position_x, node_data.position_y)
        node.name = str(node_data.id) + node_data.key
        node.skill_key = node_data.key
        node.max_points = node_data.max_points
        node.needed_neighbour_points = node_data.needed_neighbour_point_sum
        node.id = node_data.id
        node.tree = self
        add_child(node)
        if image_data.has(node.skill_key):
            node.skill_image.texture = image_data[node.skill_key]
        skill_nodes[int(node_data.id)] = node
    var root = skill_nodes.values()[0]
    root.is_root = true
    set_deferred("scroll_offset", root.position_offset * zoom - (size / 2))


    for connection in json.connections:
        var from_node = skill_nodes[int(connection.from_skill_id)]
        var to_node = skill_nodes[int(connection.to_skill_id)]

        if from_node and to_node:
            var skill_tree_connection = skill_tree_connection_scene.instantiate() as SkillTreeConnection
            skill_tree_connection.from = from_node
            skill_tree_connection.to = to_node
            skill_tree_connection.id = connection.id
            skill_connections[int(skill_tree_connection.id)] = skill_tree_connection
            from_node.connections.append(skill_tree_connection)
            to_node.connections.append(skill_tree_connection)
            add_child(skill_tree_connection)

func pan_to_node(node: SkillTreeNode):
    var move_tween = TweenHelper.tween("move", self)
    move_tween.tween_property(self, "scroll_offset", node.position_offset * zoom - (size / 2), 0.33).set_trans(Tween.TRANS_CIRC).set_ease(Tween.EASE_OUT)
