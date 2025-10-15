#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
APK版本检查功能测试脚本
测试Android应用的版本检查API
"""

import requests
import json
import time

def test_apk_version_api():
    """测试APK版本检查API"""
    print("=" * 50)
    print("APK版本检查功能测试")
    print("=" * 50)
    
    # 测试API端点
    api_url = "http://localhost:5000/api/apk/version"
    
    try:
        print("📡 测试APK版本检查API...")
        response = requests.get(api_url, timeout=10)
        
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("✅ API响应成功!")
            print("📋 版本信息:")
            print(f"  版本号: {data.get('version_code', 'N/A')}")
            print(f"  版本名称: {data.get('version_name', 'N/A')}")
            print(f"  更新说明: {data.get('update_notes', 'N/A')}")
            print(f"  下载链接: {data.get('download_url', 'N/A')}")
            print(f"  文件大小: {data.get('file_size', 0)} 字节")
            
            # 模拟Android应用版本检查逻辑
            print("\n🤖 模拟Android应用版本检查:")
            current_version = "250918"  # 模拟当前应用版本
            server_version = data.get('version_code', '')
            
            print(f"  当前应用版本: {current_version}")
            print(f"  服务器版本: {server_version}")
            
            # 简单的版本号比较（去除非数字字符后比较）
            current_num = int(''.join(filter(str.isdigit, current_version)))
            server_num = int(''.join(filter(str.isdigit, server_version)))
            
            if server_num > current_num:
                print("  🆕 发现新版本，需要更新!")
                print("  📱 弹窗将显示更新信息")
            else:
                print("  ✅ 当前版本已是最新版本")
                
        elif response.status_code == 404:
            print("⚠️ 暂无可用版本")
        else:
            print(f"❌ API请求失败: {response.status_code}")
            print(f"响应内容: {response.text}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 网络请求失败: {e}")
    except json.JSONDecodeError as e:
        print(f"❌ JSON解析失败: {e}")
    except Exception as e:
        print(f"❌ 测试失败: {e}")

def test_admin_panel():
    """测试管理员面板"""
    print("\n" + "=" * 50)
    print("管理员面板测试")
    print("=" * 50)
    
    try:
        # 测试登录页面
        print("🔐 测试管理员登录页面...")
        login_response = requests.get("http://localhost:5000/admin/login", timeout=10)
        print(f"登录页面状态码: {login_response.status_code}")
        
        if login_response.status_code == 200:
            print("✅ 登录页面访问成功")
        else:
            print("❌ 登录页面访问失败")
            
        # 测试APK版本列表（需要登录）
        print("\n📋 测试APK版本列表...")
        list_response = requests.get("http://localhost:5000/admin/apk/list", timeout=10)
        print(f"版本列表状态码: {list_response.status_code}")
        
        if list_response.status_code == 200:
            print("✅ APK版本列表访问成功")
        elif list_response.status_code == 403:
            print("⚠️ 需要管理员登录（正常行为）")
        else:
            print(f"❌ APK版本列表访问失败: {list_response.status_code}")
            
    except Exception as e:
        print(f"❌ 管理员面板测试失败: {e}")

if __name__ == "__main__":
    print("🚀 开始APK版本检查功能测试...")
    print("📝 请确保服务器正在运行: python app.py")
    print()
    
    # 等待一下让用户看到提示
    time.sleep(1)
    
    # 测试APK版本API
    test_apk_version_api()
    
    # 测试管理员面板
    test_admin_panel()
    
    print("\n" + "=" * 50)
    print("测试完成")
    print("=" * 50)
    print()
    print("使用说明：")
    print("1. 确保服务器正在运行：python app.py")
    print("2. 访问管理员面板：http://localhost:5000/admin/login")
    print("3. 使用密码 '1533' 登录")
    print("4. 点击'添加APK版本'按钮添加版本信息")
    print("5. 测试API：http://localhost:5000/api/apk/version")
    print("6. 在Android应用中测试版本检查功能")
