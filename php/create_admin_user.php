<?php
header('Content-Type: application/json');
require_once 'db_config.php'; // 确保你已经有了数据库连接文件

$email = $_POST['email'] ?? '';
$password = $_POST['password'] ?? '';
$name = $_POST['name'] ?? '';
$role = $_POST['role'] ?? 'user';
$admin_token = $_POST['admin_token'] ?? '';

// 简单的安全验证
if ($admin_token !== 'secret_admin_key') {
    echo json_encode(["success" => false, "message" => "未授权的操作"]);
    exit;
}

if (empty($email) || empty($password) || empty($name)) {
    echo json_encode(["success" => false, "message" => "请填写完整信息"]);
    exit;
}

try {
    // 1. 检查邮箱是否已存在
    $checkStmt = $pdo->prepare("SELECT id FROM admin_users WHERE email = ?");
    $checkStmt->execute([$email]);
    if ($checkStmt->fetch()) {
        echo json_encode(["success" => false, "message" => "该邮箱已被注册"]);
        exit;
    }

    // 2. 插入新用户 (实际项目中建议使用 password_hash 对密码加密)
    $stmt = $pdo->prepare("INSERT INTO admin_users (email, password, name, role) VALUES (?, ?, ?, ?)");
    $result = $stmt->execute([$email, $password, $name, $role]);

    if ($result) {
        echo json_encode(["success" => true, "message" => "用户创建成功"]);
    } else {
        echo json_encode(["success" => false, "message" => "写入数据库失败"]);
    }
} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "服务器错误: " . $e->getMessage()]);
}
?>