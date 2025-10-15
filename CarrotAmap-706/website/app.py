from flask import Flask, request, render_template, jsonify, send_file, session, redirect, url_for, flash
import sqlite3
import os
import json
from datetime import datetime
from werkzeug.utils import secure_filename
import csv
import io

app = Flask(__name__)
app.secret_key = 'your-secret-key-change-this'  # 生产环境中请更改此密钥

# 配置上传文件夹
UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif', 'webp'}
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 限制上传文件大小为16MB

# 确保上传文件夹存在
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# 管理员密码
ADMIN_PASSWORD = '1533'

def allowed_file(filename):
    """检查文件扩展名是否允许"""
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def init_db():
    """初始化数据库"""
    conn = sqlite3.connect('feedback.db')
    cursor = conn.cursor()
    
    # 创建反馈表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            time TEXT NOT NULL,
            feedback TEXT NOT NULL,
            images TEXT,  -- JSON格式存储图片路径
            note TEXT DEFAULT '',  -- 管理员备注
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    # 创建APK版本表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS apk_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            version_code TEXT NOT NULL UNIQUE,  -- 版本号，如250909
            version_name TEXT,  -- 版本名称，如v2.5.9
            update_notes TEXT NOT NULL,  -- 更新说明
            download_url TEXT NOT NULL,  -- APK下载链接
            file_size INTEGER DEFAULT 0,  -- 文件大小（字节），可选
            upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            is_active BOOLEAN DEFAULT 1  -- 是否为当前活跃版本
        )
    ''')

    # 创建赞助表（仅三列：id、赞助金额、提交时间）
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS donations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount REAL NOT NULL,  -- 赞助金额
            device_id TEXT,  -- 设备ID（可选）
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 提交时间
        )
    ''')

    # donations 表结构迁移：兼容旧库，确保存在 device_id 列
    try:
        cursor.execute("PRAGMA table_info(donations)")
        donation_cols = [c[1] for c in cursor.fetchall()]
        if 'device_id' not in donation_cols:
            cursor.execute('ALTER TABLE donations ADD COLUMN device_id TEXT')
    except Exception:
        pass
    
    # 数据库迁移：检查并更新现有表结构
    migrate_database(cursor)
    
    conn.commit()
    conn.close()

def migrate_database(cursor):
    """数据库迁移：更新现有表结构"""
    try:
        # 检查apk_versions表是否存在
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='apk_versions'")
        if cursor.fetchone():
            # 检查是否存在download_url列
            cursor.execute("PRAGMA table_info(apk_versions)")
            columns = [column[1] for column in cursor.fetchall()]
            
            if 'download_url' not in columns:
                print("正在更新数据库表结构...")
                
                # 如果存在file_path列，需要迁移数据
                if 'file_path' in columns:
                    # 创建临时表
                    cursor.execute('''
                        CREATE TABLE apk_versions_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            version_code TEXT NOT NULL UNIQUE,
                            version_name TEXT,
                            update_notes TEXT NOT NULL,
                            download_url TEXT NOT NULL,
                            file_size INTEGER DEFAULT 0,
                            upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            is_active BOOLEAN DEFAULT 1
                        )
                    ''')
                    
                    # 迁移现有数据（将file_path作为download_url）
                    cursor.execute('''
                        INSERT INTO apk_versions_new 
                        (id, version_code, version_name, update_notes, download_url, file_size, upload_time, is_active)
                        SELECT id, version_code, version_name, update_notes, 
                               CASE 
                                   WHEN file_path LIKE 'http%' THEN file_path
                                   ELSE '/apks/' || file_path
                               END as download_url,
                               file_size, upload_time, is_active
                        FROM apk_versions
                    ''')
                    
                    # 删除旧表，重命名新表
                    cursor.execute('DROP TABLE apk_versions')
                    cursor.execute('ALTER TABLE apk_versions_new RENAME TO apk_versions')
                    
                    print("数据库表结构更新完成")
                else:
                    # 如果不存在file_path列，直接添加download_url列
                    cursor.execute('ALTER TABLE apk_versions ADD COLUMN download_url TEXT')
                    print("已添加download_url列")
            else:
                print("数据库表结构已是最新版本")
        else:
            print("apk_versions表不存在，将创建新表")
            
    except Exception as e:
        print(f"数据库迁移失败: {e}")
        # 如果迁移失败，尝试重新创建表
        try:
            cursor.execute('DROP TABLE IF EXISTS apk_versions')
            cursor.execute('''
                CREATE TABLE apk_versions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    version_code TEXT NOT NULL UNIQUE,
                    version_name TEXT,
                    update_notes TEXT NOT NULL,
                    download_url TEXT NOT NULL,
                    file_size INTEGER DEFAULT 0,
                    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT 1
                )
            ''')
            print("已重新创建apk_versions表")
        except Exception as e2:
            print(f"重新创建表失败: {e2}")

def get_db_connection():
    """获取数据库连接"""
    conn = sqlite3.connect('feedback.db')
    conn.row_factory = sqlite3.Row
    return conn

@app.route('/')
def index():
    """主页 - 显示所有反馈信息"""
    conn = get_db_connection()
    feedbacks = conn.execute(
        'SELECT * FROM feedback ORDER BY created_at DESC'
    ).fetchall()
    conn.close()
    
    # 将数据库行转换为字典列表
    feedback_list = []
    for feedback in feedbacks:
        feedback_dict = dict(feedback)
        # 解析图片JSON
        if feedback_dict['images']:
            try:
                feedback_dict['images'] = json.loads(feedback_dict['images'])
            except:
                feedback_dict['images'] = []
        else:
            feedback_dict['images'] = []
        feedback_list.append(feedback_dict)
    
    return render_template('index.html', feedbacks=feedback_list)

@app.route('/api/feedback', methods=['POST'])
def submit_feedback():
    """API接口 - 接收反馈数据"""
    try:
        # 获取表单数据
        user_id = request.form.get('id')
        time_str = request.form.get('time')
        feedback_text = request.form.get('feedback')
        
        if not all([user_id, time_str, feedback_text]):
            return jsonify({'error': '缺少必要参数'}), 400
        
        # 处理上传的图片
        uploaded_files = request.files.getlist('images')
        image_paths = []
        
        for file in uploaded_files:
            if file and allowed_file(file.filename):
                # 生成安全的文件名
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                filename = f"{user_id}_{timestamp}_{secure_filename(file.filename)}"
                filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
                file.save(filepath)
                image_paths.append(filename)
        
        # 保存到数据库
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute('''
            INSERT INTO feedback (user_id, time, feedback, images)
            VALUES (?, ?, ?, ?)
        ''', (user_id, time_str, feedback_text, json.dumps(image_paths)))
        
        conn.commit()
        conn.close()
        
        return jsonify({'status': 'success', 'message': '反馈提交成功'}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/login', methods=['GET', 'POST'])
def admin_login():
    """管理员登录页面"""
    if request.method == 'POST':
        password = request.form.get('password')
        if password == ADMIN_PASSWORD:
            session['admin'] = True
            flash('登录成功', 'success')
            return redirect(url_for('admin_panel'))
        else:
            flash('密码错误', 'error')
    
    return render_template('admin_login.html')

@app.route('/admin')
def admin_panel():
    """管理员面板"""
    if not session.get('admin'):
        return redirect(url_for('admin_login'))
    
    conn = get_db_connection()
    feedbacks = conn.execute(
        'SELECT * FROM feedback ORDER BY created_at DESC'
    ).fetchall()
    conn.close()
    
    # 将数据库行转换为字典列表
    feedback_list = []
    for feedback in feedbacks:
        feedback_dict = dict(feedback)
        # 解析图片JSON
        if feedback_dict['images']:
            try:
                feedback_dict['images'] = json.loads(feedback_dict['images'])
            except:
                feedback_dict['images'] = []
        else:
            feedback_dict['images'] = []
        feedback_list.append(feedback_dict)
    
    return render_template('admin_panel.html', feedbacks=feedback_list)

@app.route('/admin/delete/<int:feedback_id>', methods=['POST'])
def delete_feedback(feedback_id):
    """删除反馈"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # 获取图片路径以便删除文件
        feedback = cursor.execute(
            'SELECT images FROM feedback WHERE id = ?', (feedback_id,)
        ).fetchone()
        
        if feedback and feedback['images']:
            try:
                image_paths = json.loads(feedback['images'])
                for image_path in image_paths:
                    full_path = os.path.join(app.config['UPLOAD_FOLDER'], image_path)
                    if os.path.exists(full_path):
                        os.remove(full_path)
            except:
                pass
        
        # 删除数据库记录
        cursor.execute('DELETE FROM feedback WHERE id = ?', (feedback_id,))
        conn.commit()
        conn.close()
        
        return jsonify({'status': 'success'}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/update_note/<int:feedback_id>', methods=['POST'])
def update_note(feedback_id):
    """更新备注"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        note = request.json.get('note', '')
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute(
            'UPDATE feedback SET note = ? WHERE id = ?',
            (note, feedback_id)
        )
        
        conn.commit()
        conn.close()
        
        return jsonify({'status': 'success'}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/export')
def export_data():
    """导出数据"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        conn = get_db_connection()
        feedbacks = conn.execute(
            'SELECT * FROM feedback ORDER BY created_at DESC'
        ).fetchall()
        conn.close()
        
        # 创建CSV文件
        output = io.StringIO()
        writer = csv.writer(output)
        
        # 写入表头
        writer.writerow(['ID', '用户ID', '时间', '反馈内容', '图片数量', '备注', '创建时间'])
        
        # 写入数据
        for feedback in feedbacks:
            image_count = 0
            if feedback['images']:
                try:
                    image_paths = json.loads(feedback['images'])
                    image_count = len(image_paths)
                except:
                    pass
            
            writer.writerow([
                feedback['id'],
                feedback['user_id'],
                feedback['time'],
                feedback['feedback'],
                image_count,
                feedback['note'] or '',
                feedback['created_at']
            ])
        
        # 准备下载
        output.seek(0)
        
        # 创建字节流
        mem = io.BytesIO()
        mem.write(output.getvalue().encode('utf-8-sig'))  # 使用UTF-8 BOM以支持中文
        mem.seek(0)
        
        return send_file(
            mem,
            as_attachment=True,
            download_name=f'feedback_export_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv',
            mimetype='text/csv'
        )
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ==============================
# 赞助（Donations）相关 API
# ==============================

@app.route('/api/donation', methods=['POST'])
def add_donation():
    """新增一条赞助记录（POST）
    请求参数（表单或JSON均可）：
    - amount: float 金额（必填）
    - device_id: str 设备ID（可选）。如果提供，则同一 device_id 仅保留最新一条记录
    """
    try:
        amount = None
        device_id = None
        # 优先JSON
        if request.is_json:
            amount = request.json.get('amount')
            device_id = request.json.get('device_id')
        # 回退表单
        if amount is None:
            amount = request.form.get('amount')
        if device_id is None:
            device_id = request.form.get('device_id')

        # 校验金额
        try:
            amount = float(amount)
        except Exception:
            return jsonify({'error': '金额格式不正确'}), 400

        if amount <= 0:
            return jsonify({'error': '金额必须大于0'}), 400

        conn = get_db_connection()
        cursor = conn.cursor()

        # 如果提供了 device_id，则删除该 device_id 的旧记录，保证只保留最新一条
        if device_id and str(device_id).strip():
            cursor.execute('DELETE FROM donations WHERE device_id = ?', (device_id.strip(),))

        cursor.execute('INSERT INTO donations (amount, device_id) VALUES (?, ?)', (amount, device_id))
        conn.commit()

        # 返回新纪录的ID与时间
        new_id = cursor.lastrowid
        row = cursor.execute('SELECT id, amount, device_id, created_at FROM donations WHERE id = ?', (new_id,)).fetchone()
        conn.close()

        return jsonify({
            'status': 'success',
            'data': {
                'id': row['id'],
                'amount': row['amount'],
                'device_id': row['device_id'],
                'created_at': row['created_at']
            }
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/admin/donations/list', methods=['GET'])
def admin_list_donations():
    """管理员：获取赞助记录列表"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    try:
        conn = get_db_connection()
        rows = conn.execute(
            'SELECT id, amount, device_id, created_at FROM donations ORDER BY created_at DESC, id DESC'
        ).fetchall()
        conn.close()
        data = [
            {
                'id': r['id'],
                'amount': r['amount'],
                'device_id': r['device_id'],
                'created_at': r['created_at']
            } for r in rows
        ]
        return jsonify({'status': 'success', 'count': len(data), 'data': data}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/admin/donations/delete/<int:donation_id>', methods=['POST'])
def admin_delete_donation(donation_id):
    """管理员：删除一条赞助记录"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute('DELETE FROM donations WHERE id = ?', (donation_id,))
        conn.commit()
        conn.close()
        return jsonify({'status': 'success'}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/donations', methods=['GET'])
def list_donations():
    """获取最近100条赞助记录（GET）"""
    try:
        conn = get_db_connection()
        rows = conn.execute(
            'SELECT id, amount, device_id, created_at FROM donations ORDER BY created_at DESC, id DESC LIMIT 100'
        ).fetchall()
        conn.close()

        data = [
            {
                'id': r['id'],
                'amount': r['amount'],
                'device_id': r['device_id'],
                'created_at': r['created_at']
            } for r in rows
        ]

        return jsonify({'status': 'success', 'count': len(data), 'data': data}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/logout')
def admin_logout():
    """管理员登出"""
    session.pop('admin', None)
    flash('已登出', 'info')
    return redirect(url_for('admin_login'))

@app.route('/uploads/<filename>')
def uploaded_file(filename):
    """提供上传文件的访问"""
    return send_file(os.path.join(app.config['UPLOAD_FOLDER'], filename))


# APK管理相关路由
@app.route('/admin/apk/add', methods=['POST'])
def add_apk_version():
    """添加APK版本信息"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        # 获取表单数据
        version_code = request.form.get('version_code')
        version_name = request.form.get('version_name', '')
        update_notes = request.form.get('update_notes')
        download_url = request.form.get('download_url')
        file_size = request.form.get('file_size', '0')
        
        if not all([version_code, update_notes, download_url]):
            return jsonify({'error': '缺少必要参数'}), 400
        
        # 验证URL格式
        if not download_url.startswith(('http://', 'https://')):
            return jsonify({'error': '下载链接格式不正确'}), 400
        
        # 转换文件大小为整数
        try:
            file_size = int(file_size) if file_size else 0
        except ValueError:
            file_size = 0
        
        # 保存到数据库
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # 将之前的版本设为非活跃
        cursor.execute('UPDATE apk_versions SET is_active = 0')
        
        # 插入新版本
        cursor.execute('''
            INSERT INTO apk_versions (version_code, version_name, update_notes, download_url, file_size)
            VALUES (?, ?, ?, ?, ?)
        ''', (version_code, version_name, update_notes, download_url, file_size))
        
        conn.commit()
        conn.close()
        
        return jsonify({'status': 'success', 'message': 'APK版本信息添加成功'}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/apk/list')
def list_apk_versions():
    """获取APK版本列表（管理员）"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        conn = get_db_connection()
        apk_versions = conn.execute(
            'SELECT * FROM apk_versions ORDER BY upload_time DESC'
        ).fetchall()
        conn.close()
        
        # 转换为字典列表
        versions_list = []
        for version in apk_versions:
            version_dict = dict(version)
            versions_list.append(version_dict)
        
        return jsonify({'status': 'success', 'versions': versions_list}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/admin/apk/delete/<int:version_id>', methods=['POST'])
def delete_apk_version(version_id):
    """删除APK版本"""
    if not session.get('admin'):
        return jsonify({'error': '未授权'}), 403
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # 删除数据库记录
        cursor.execute('DELETE FROM apk_versions WHERE id = ?', (version_id,))
        conn.commit()
        conn.close()
        
        return jsonify({'status': 'success'}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/apk/version')
def get_latest_apk_version():
    """获取最新APK版本信息（公开API）"""
    try:
        conn = get_db_connection()
        latest_version = conn.execute(
            'SELECT * FROM apk_versions WHERE is_active = 1 ORDER BY upload_time DESC LIMIT 1'
        ).fetchone()
        conn.close()
        
        if not latest_version:
            return jsonify({'error': '暂无可用版本'}), 404
        
        # 直接使用存储的下载链接
        download_url = latest_version['download_url']
        
        return jsonify({
            'status': 'success',
            'version_code': latest_version['version_code'],
            'version_name': latest_version['version_name'] or f"v{latest_version['version_code']}",
            'update_notes': latest_version['update_notes'],
            'download_url': download_url,
            'file_size': latest_version['file_size'],
            'upload_time': latest_version['upload_time']
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    # 初始化数据库
    init_db()
    
    # 启动应用
    app.run(debug=True, host='0.0.0.0', port=5000)
