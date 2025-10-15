#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
APK版本检查API测试脚本
用于测试APK版本管理功能
"""

import requests
import urllib3

# 禁用SSL警告（如果证书有问题）
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 服务器地址
BASE_URL = "https://app.mspa.shop"

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
            print(f"上传时间: {data.get('upload_time', 'N/A')}")
            return True
        elif response.status_code == 404:
            print("ℹ 暂无可用版本（这是正常的，如果还没有上传APK）")
            return True
        else:
            print(f"✗ API调用失败: {response.text}")
            return False
            
    except Exception as e:
        print(f"✗ 请求失败: {e}")
        return False

def test_apk_download():
    """测试APK文件下载"""
    print("\n测试APK文件下载...")
    
    try:
        # 先获取版本信息
        response = requests.get(f"{BASE_URL}/api/apk/version", verify=False)
        if response.status_code != 200:
            print("ℹ 无法获取版本信息，跳过下载测试")
            return True
        
        data = response.json()
        download_url = data.get('download_url')
        
        if not download_url:
            print("ℹ 没有下载链接，跳过下载测试")
            return True
        
        # 测试下载链接
        download_response = requests.head(download_url, verify=False)
        print(f"下载链接状态码: {download_response.status_code}")
        
        if download_response.status_code == 200:
            print("✓ APK文件可以正常下载")
            return True
        else:
            print(f"✗ APK文件下载失败: {download_response.status_code}")
            return False
            
    except Exception as e:
        print(f"✗ 下载测试失败: {e}")
        return False

def main():
    """主测试函数"""
    print("=" * 50)
    print("APK版本管理 API 测试")
    print("目标服务器: https://app.mspa.shop")
    print("=" * 50)
    
    # 检查服务器是否运行
    try:
        response = requests.get(f"{BASE_URL}/", timeout=10, verify=False)
        print("✓ 服务器正在运行")
        print(f"✓ 服务器响应时间: {response.elapsed.total_seconds():.2f}秒")
    except:
        print("✗ 服务器未运行或无法访问")
        print("请检查服务器状态或网络连接")
        return
    
    # 执行测试
    tests = [
        ("APK版本检查API", test_apk_version_api),
        ("APK文件下载", test_apk_download),
    ]
    
    results = []
    for test_name, test_func in tests:
        print(f"\n{'='*20} {test_name} {'='*20}")
        result = test_func()
        results.append((test_name, result))
        print(f"结果: {'✓ 通过' if result else '✗ 失败'}")
    
    # 总结
    print(f"\n{'='*50}")
    print("测试总结:")
    print(f"{'='*50}")
    
    passed = 0
    for test_name, result in results:
        status = "✓ 通过" if result else "✗ 失败"
        print(f"{test_name}: {status}")
        if result:
            passed += 1
    
    print(f"\n总计: {passed}/{len(results)} 个测试通过")
    
    if passed == len(results):
        print("🎉 所有测试都通过了！")
    else:
        print("⚠️  部分测试失败，请检查服务器配置")
    
    print(f"\n💡 提示:")
    print(f"- 如果显示'暂无可用版本'，请先通过管理员面板上传APK文件")
    print(f"- 管理员登录地址: {BASE_URL}/admin/login")
    print(f"- 管理员密码: 1533")

if __name__ == "__main__":
    main()
