#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
APK版本检查API测试脚本（新版本 - 使用下载链接）
用于测试APK版本管理功能
"""

import requests
import urllib3

# 禁用SSL警告（如果证书有问题）
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 服务器地址
BASE_URL = "http://localhost:5000"

def test_apk_version_api():
    """测试APK版本检查API"""
    print("测试APK版本检查API...")
    
    try:
        response = requests.get(f"{BASE_URL}/api/apk/version", verify=False)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("✓ API调用成功")
            print(f"版本号: {data.get('version_code', 'N/A')}")
            print(f"版本名称: {data.get('version_name', 'N/A')}")
            print(f"更新说明: {data.get('update_notes', 'N/A')}")
            print(f"下载链接: {data.get('download_url', 'N/A')}")
            print(f"文件大小: {data.get('file_size', 'N/A')} 字节")
            print(f"添加时间: {data.get('upload_time', 'N/A')}")
        elif response.status_code == 404:
            print("⚠️ 暂无可用版本")
        else:
            print(f"❌ API调用失败: {response.text}")
            
    except requests.exceptions.ConnectionError:
        print("❌ 连接失败，请确保服务器正在运行")
    except Exception as e:
        print(f"❌ 测试失败: {e}")

def test_admin_login():
    """测试管理员登录"""
    print("\n测试管理员登录...")
    
    try:
        # 获取登录页面
        response = requests.get(f"{BASE_URL}/admin/login", verify=False)
        if response.status_code == 200:
            print("✓ 登录页面访问成功")
        else:
            print(f"❌ 登录页面访问失败: {response.status_code}")
            
    except Exception as e:
        print(f"❌ 登录测试失败: {e}")

def test_admin_apk_list():
    """测试管理员APK版本列表（需要登录）"""
    print("\n测试管理员APK版本列表...")
    
    try:
        response = requests.get(f"{BASE_URL}/admin/apk/list", verify=False)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 403:
            print("⚠️ 需要管理员登录（正常行为）")
        elif response.status_code == 200:
            data = response.json()
            print("✓ APK版本列表获取成功")
            print(f"版本数量: {len(data.get('versions', []))}")
        else:
            print(f"❌ 获取失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 测试失败: {e}")

if __name__ == "__main__":
    print("=" * 50)
    print("APK版本管理API测试")
    print("=" * 50)
    
    test_apk_version_api()
    test_admin_login()
    test_admin_apk_list()
    
    print("\n" + "=" * 50)
    print("测试完成")
    print("=" * 50)
    
    print("\n使用说明：")
    print("1. 确保服务器正在运行：python app.py")
    print("2. 访问管理员面板：http://localhost:5000/admin/login")
    print("3. 使用密码 '1533' 登录")
    print("4. 点击'添加APK版本'按钮添加版本信息")
    print("5. 测试API：http://localhost:5000/api/apk/version")

