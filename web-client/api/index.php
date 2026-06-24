<?php
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");

if ($_SERVER["REQUEST_METHOD"] === "OPTIONS") {
    exit;
}

$devicesFile = __DIR__ . "/devices.json";

function getDevices() {
    global $devicesFile;
    if (!file_exists($devicesFile)) {
        return ["devices" => []];
    }

    $data = json_decode(file_get_contents($devicesFile), true);
    return is_array($data) && isset($data["devices"]) && is_array($data["devices"])
        ? $data
        : ["devices" => []];
}

function saveDevices($data) {
    global $devicesFile;
    file_put_contents(
        $devicesFile,
        json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE),
        LOCK_EX
    );
}

function getDeviceKey($device) {
    if (!empty($device["deviceId"])) {
        return "id:" . $device["deviceId"];
    }

    $model = isset($device["model"]) ? trim($device["model"]) : "";
    $name = isset($device["name"]) ? trim($device["name"]) : "";
    if ($model !== "" || $name !== "") {
        return "device:" . $model . ":" . $name;
    }

    return "ip:" . (isset($device["ip"]) ? $device["ip"] : "");
}

function getLastSeenTimestamp($device) {
    if (empty($device["lastSeen"])) {
        return 0;
    }

    $timestamp = strtotime($device["lastSeen"]);
    return $timestamp === false ? 0 : $timestamp;
}

function keepLatestDevices($devices) {
    $latest = [];

    foreach ($devices as $device) {
        $key = getDeviceKey($device);
        if (!isset($latest[$key]) || getLastSeenTimestamp($device) >= getLastSeenTimestamp($latest[$key])) {
            $latest[$key] = $device;
        }
    }

    $devices = array_values($latest);
    usort($devices, function ($left, $right) {
        return getLastSeenTimestamp($right) - getLastSeenTimestamp($left);
    });
    return $devices;
}

if ($_SERVER["REQUEST_METHOD"] === "GET") {
    $data = getDevices();
    $latestDevices = keepLatestDevices($data["devices"]);

    if (count($latestDevices) !== count($data["devices"])) {
        $data["devices"] = $latestDevices;
        saveDevices($data);
    } else {
        $data["devices"] = $latestDevices;
    }

    header("Content-Type: application/json; charset=utf-8");
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

if ($_SERVER["REQUEST_METHOD"] === "POST") {
    $input = json_decode(file_get_contents("php://input"), true);

    if (is_array($input) && !empty($input["ip"])) {
        $data = getDevices();
        $input["lastSeen"] = date("c");
        $inputKey = getDeviceKey($input);

        $devices = array_filter($data["devices"], function ($device) use ($input, $inputKey) {
            $sameIdentity = getDeviceKey($device) === $inputKey;
            $sameIp = isset($device["ip"]) && $device["ip"] === $input["ip"];
            return !$sameIdentity && !$sameIp;
        });
        $devices[] = $input;

        $data["devices"] = keepLatestDevices($devices);
        saveDevices($data);

        header("Content-Type: application/json; charset=utf-8");
        echo json_encode(["success" => true]);
        exit;
    }

    header("Content-Type: application/json; charset=utf-8");
    echo json_encode(["success" => false, "error" => "Invalid data"]);
    exit;
}

header("HTTP/1.1 404 Not Found");
echo "Not Found";
