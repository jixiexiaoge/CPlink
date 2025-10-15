from flask import Flask, render_template, request, jsonify, session, redirect, url_for, flash
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime
import os
from functools import wraps

app = Flask(__name__)

# 配置数据库和会话
basedir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{os.path.join(basedir, "database.db")}'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SECRET_KEY'] = 'Flow2025_secret_key_for_session_management'

db = SQLAlchemy(app)

# 用户信息表
class User(db.Model):
    __tablename__ = 'users'
    
    id = db.Column(db.Integer, primary_key=True)
    device_id = db.Column(db.String(100), unique=True, nullable=False)
    usage_count = db.Column(db.Integer, default=0)
    usage_duration = db.Column(db.Float, default=0.0)  # 使用时长（小时）
    total_distance = db.Column(db.Float, default=0.0)  # 累计距离（公里）
    modify_time = db.Column(db.DateTime, default=datetime.utcnow)
    sponsor_amount = db.Column(db.Float, default=0.0)  # 赞助金额
    user_type = db.Column(db.Integer, default=0)  # 用户等级：-1-管理员专用，0-未知用户，1-新用户，2-支持者，3-赞助者，4-铁粉
    car_model = db.Column(db.String(100), default='')  # 车型信息
    wechat_name = db.Column(db.String(100), default='')  # 微信名
    
    def to_dict(self):
        return {
            'device_id': self.device_id,
            'usage_count': self.usage_count,
            'usage_duration': self.usage_duration,
            'total_distance': self.total_distance,
            'modify_time': self.modify_time.isoformat() if self.modify_time else None,
            'sponsor_amount': self.sponsor_amount,
            'user_type': self.user_type,
            'car_model': self.car_model,
            'wechat_name': self.wechat_name
        }

# 日志表
class Log(db.Model):
    __tablename__ = 'logs'
    
    id = db.Column(db.Integer, primary_key=True)
    device_id = db.Column(db.String(100), nullable=False)
    log_time = db.Column(db.DateTime, default=datetime.utcnow)
    operation_record = db.Column(db.Text, nullable=False)
    
    def to_dict(self):
        return {
            'device_id': self.device_id,
            'log_time': self.log_time.isoformat() if self.log_time else None,
            'operation_record': self.operation_record
        }

# 视频表
class Video(db.Model):
    __tablename__ = 'videos'
    
    id = db.Column(db.Integer, primary_key=True)
    video_title = db.Column(db.String(200), nullable=False)
    video_link = db.Column(db.String(500), nullable=False)
    
    def to_dict(self):
        return {
            'id': self.id,
            'video_title': self.video_title,
            'video_link': self.video_link
        }

# 创建数据库表
with app.app_context():
    db.create_all()
    
    # 添加测试数据
    def add_test_data():
        # 检查是否已有用户数据
        if User.query.count() == 0:
            # 添加测试用户数据
            test_users = [
                User(
                    device_id='DEVICE001',
                    usage_count=25,
                    usage_duration=45.5,
                    total_distance=1200.0,
                    sponsor_amount=100.0,
                    user_type=2,  # 支持者
                    car_model='特斯拉 Model 3',
                    wechat_name='特斯拉车主小王',
                    modify_time=datetime.utcnow()
                ),
                User(
                    device_id='DEVICE002',
                    usage_count=18,
                    usage_duration=32.8,
                    total_distance=850.0,
                    sponsor_amount=50.0,
                    user_type=1,  # 新用户
                    car_model='比亚迪 汉EV',
                    wechat_name='比亚迪汉EV用户',
                    modify_time=datetime.utcnow()
                ),
                User(
                    device_id='DEVICE003',
                    usage_count=35,
                    usage_duration=68.2,
                    total_distance=2100.0,
                    sponsor_amount=200.0,
                    user_type=3,  # 赞助者
                    car_model='蔚来 ES6',
                    wechat_name='蔚来ES6车主',
                    modify_time=datetime.utcnow()
                ),
                User(
                    device_id='DEVICE004',
                    usage_count=12,
                    usage_duration=18.5,
                    total_distance=450.0,
                    sponsor_amount=25.0,
                    user_type=0,  # 未知用户
                    car_model='小鹏 P7',
                    wechat_name='小鹏P7新用户',
                    modify_time=datetime.utcnow()
                ),
                User(
                    device_id='DEVICE005',
                    usage_count=42,
                    usage_duration=85.3,
                    total_distance=3200.0,
                    sponsor_amount=300.0,
                    user_type=4,  # 铁粉
                    car_model='理想 ONE',
                    wechat_name='理想ONE车主',
                    modify_time=datetime.utcnow()
                )
            ]
            
            for user in test_users:
                db.session.add(user)
            
            # 添加测试日志数据
            test_logs = [
                Log(
                    device_id='DEVICE001',
                    operation_record='用户启动应用，开始导航',
                    log_time=datetime.utcnow()
                ),
                Log(
                    device_id='DEVICE002',
                    operation_record='用户完成一次长途驾驶，距离150km',
                    log_time=datetime.utcnow()
                ),
                Log(
                    device_id='DEVICE003',
                    operation_record='用户使用语音控制功能',
                    log_time=datetime.utcnow()
                ),
                Log(
                    device_id='DEVICE004',
                    operation_record='新用户首次登录，完成设备绑定',
                    log_time=datetime.utcnow()
                ),
                Log(
                    device_id='DEVICE005',
                    operation_record='用户分享驾驶数据到社交平台',
                    log_time=datetime.utcnow()
                )
            ]
            
            for log in test_logs:
                db.session.add(log)
            
            db.session.commit()
            print("测试用户和日志数据已添加完成！")
        
        # 检查是否已有视频数据
        if Video.query.count() == 0:
            # 添加测试视频数据
            test_videos = [
                Video(
                    video_title='CPlink使用教程 - 基础操作',
                    video_link='https://example.com/video1'
                ),
                Video(
                    video_title='高级功能演示 - 语音控制',
                    video_link='https://example.com/video2'
                ),
                Video(
                    video_title='安全驾驶技巧分享',
                    video_link='https://example.com/video3'
                )
            ]
            
            for video in test_videos:
                db.session.add(video)
            
            db.session.commit()
            print("测试视频数据已添加完成！")
    
    add_test_data()

# 认证装饰器
def login_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not session.get('logged_in'):
            return redirect(url_for('admin_login'))
        return f(*args, **kwargs)
    return decorated_function

# 管理员密码
ADMIN_PASSWORD = 'Flow2025'

# API路由：获取用户数据
@app.route('/api/user/<device_id>', methods=['GET'])
def get_user_data(device_id):
    try:
        user = User.query.filter_by(device_id=device_id).first()
        if user:
            return jsonify({
                'success': True,
                'data': user.to_dict(),
                'message': '用户数据获取成功'
            })
        else:
            return jsonify({
                'success': False,
                'message': '用户不存在'
            }), 404
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# API路由：用户信息登记与确认
@app.route('/api/user/register', methods=['POST'])
def user_register():
    try:
        data = request.get_json()
        
        # 验证必需字段
        required_fields = ['device_id', 'usage_count', 'usage_duration', 'total_distance']
        for field in required_fields:
            if field not in data:
                return jsonify({'error': f'缺少必需字段: {field}'}), 400
        
        device_id = data['device_id']
        usage_count = data['usage_count']
        usage_duration = data['usage_duration']
        total_distance = data['total_distance']
        wechat_name = data.get('wechat_name', '')  # 微信名，可选字段
        
        # 查询设备ID是否已存在
        existing_user = User.query.filter_by(device_id=device_id).first()
        
        if existing_user:
            # 已登记用户，更新信息并返回用户类型
            existing_user.usage_count = usage_count
            existing_user.usage_duration = usage_duration
            existing_user.total_distance = total_distance
            if wechat_name:  # 如果提供了微信名，则更新
                existing_user.wechat_name = wechat_name
            existing_user.modify_time = datetime.utcnow()
            
            db.session.commit()
            
            return jsonify({
                'user_type': existing_user.user_type,
                'time': 200,
                'message': '用户信息已更新'
            })
        else:
            # 新用户，创建记录
            new_user = User(
                device_id=device_id,
                usage_count=usage_count,
                usage_duration=usage_duration,
                total_distance=total_distance,
                wechat_name=wechat_name,  # 添加微信名
                user_type=1,  # 设置为新用户
                modify_time=datetime.utcnow()
            )
            
            db.session.add(new_user)
            db.session.commit()
            
            return jsonify({
                'user_type': 0,
                'time': 100,
                'message': '新用户已登记'
            })
            
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# API路由：更新用户数据
@app.route('/api/user/update', methods=['POST'])
def update_user_data():
    try:
        data = request.get_json()
        
        # 验证必需字段
        if 'device_id' not in data:
            return jsonify({'error': '缺少必需字段: device_id'}), 400
        
        device_id = data['device_id']
        user = User.query.filter_by(device_id=device_id).first()
        
        if not user:
            return jsonify({'error': '用户不存在'}), 404
        
        # 更新用户数据
        if 'usage_count' in data:
            user.usage_count = data['usage_count']
        if 'usage_duration' in data:
            user.usage_duration = data['usage_duration']
        if 'total_distance' in data:
            user.total_distance = data['total_distance']
        if 'sponsor_amount' in data:
            user.sponsor_amount = data['sponsor_amount']
        if 'user_type' in data:
            user.user_type = data['user_type']
        if 'car_model' in data:
            user.car_model = data['car_model']
        if 'wechat_name' in data:
            user.wechat_name = data['wechat_name']
        
        user.modify_time = datetime.utcnow()
        db.session.commit()
        
        return jsonify({
            'success': True,
            'data': user.to_dict(),
            'message': '用户数据更新成功'
        })
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# API路由：获取视频数据
@app.route('/api/videos', methods=['GET'])
def get_videos():
    try:
        videos = Video.query.all()
        video_list = [video.to_dict() for video in videos]
        
        return jsonify({
            'success': True,
            'data': video_list,
            'count': len(video_list),
            'message': '视频数据获取成功'
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# API路由：获取单个视频数据
@app.route('/api/videos/<int:video_id>', methods=['GET'])
def get_video(video_id):
    try:
        video = Video.query.get_or_404(video_id)
        
        return jsonify({
            'success': True,
            'data': video.to_dict(),
            'message': '视频数据获取成功'
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# 网页路由
@app.route('/')
def index():
    # 按使用时长排序
    users_by_duration = User.query.order_by(User.usage_duration.desc()).all()
    # 按累计距离排序
    users_by_distance = User.query.order_by(User.total_distance.desc()).all()
    # 按使用次数排序
    users_by_count = User.query.order_by(User.usage_count.desc()).all()
    
    return render_template('index.html', 
                         users_by_duration=users_by_duration,
                         users_by_distance=users_by_distance,
                         users_by_count=users_by_count)

# 后台管理路由
@app.route('/admin/login', methods=['GET', 'POST'])
def admin_login():
    if request.method == 'POST':
        password = request.form.get('password')
        if password == ADMIN_PASSWORD:
            session['logged_in'] = True
            flash('登录成功！', 'success')
            return redirect(url_for('admin_dashboard'))
        else:
            flash('密码错误！', 'error')
    return render_template('admin/login.html')

@app.route('/admin/logout')
def admin_logout():
    session.pop('logged_in', None)
    flash('已退出登录', 'info')
    return redirect(url_for('admin_login'))

@app.route('/admin')
@login_required
def admin_dashboard():
    users_count = User.query.count()
    logs_count = Log.query.count()
    videos_count = Video.query.count()
    
    recent_users = User.query.order_by(User.modify_time.desc()).limit(5).all()
    recent_logs = Log.query.order_by(Log.log_time.desc()).limit(5).all()
    
    return render_template('admin/dashboard.html', 
                         users_count=users_count,
                         logs_count=logs_count,
                         videos_count=videos_count,
                         recent_users=recent_users,
                         recent_logs=recent_logs)

# 用户管理
@app.route('/admin/users')
@login_required
def admin_users():
    users = User.query.order_by(User.modify_time.desc()).all()
    return render_template('admin/users.html', users=users)

@app.route('/admin/users/add', methods=['GET', 'POST'])
@login_required
def admin_users_add():
    if request.method == 'POST':
        try:
            user = User(
                device_id=request.form.get('device_id'),
                usage_count=int(request.form.get('usage_count', 0)),
                usage_duration=float(request.form.get('usage_duration', 0)),
                total_distance=float(request.form.get('total_distance', 0)),
                sponsor_amount=float(request.form.get('sponsor_amount', 0)),
                user_type=int(request.form.get('user_type', 0)),
                car_model=request.form.get('car_model', ''),
                wechat_name=request.form.get('wechat_name', ''),
                modify_time=datetime.utcnow()
            )
            db.session.add(user)
            db.session.commit()
            flash('用户添加成功！', 'success')
            return redirect(url_for('admin_users'))
        except Exception as e:
            flash(f'添加失败：{str(e)}', 'error')
    return render_template('admin/user_form.html', action='add')

@app.route('/admin/users/edit/<int:user_id>', methods=['GET', 'POST'])
@login_required
def admin_users_edit(user_id):
    user = User.query.get_or_404(user_id)
    if request.method == 'POST':
        try:
            user.device_id = request.form.get('device_id')
            user.usage_count = int(request.form.get('usage_count', 0))
            user.usage_duration = float(request.form.get('usage_duration', 0))
            user.total_distance = float(request.form.get('total_distance', 0))
            user.sponsor_amount = float(request.form.get('sponsor_amount', 0))
            user.user_type = int(request.form.get('user_type', 0))
            user.car_model = request.form.get('car_model', '')
            user.wechat_name = request.form.get('wechat_name', '')
            user.modify_time = datetime.utcnow()
            db.session.commit()
            flash('用户信息更新成功！', 'success')
            return redirect(url_for('admin_users'))
        except Exception as e:
            flash(f'更新失败：{str(e)}', 'error')
    return render_template('admin/user_form.html', action='edit', user=user)

@app.route('/admin/users/delete/<int:user_id>')
@login_required
def admin_users_delete(user_id):
    try:
        user = User.query.get_or_404(user_id)
        db.session.delete(user)
        db.session.commit()
        flash('用户删除成功！', 'success')
    except Exception as e:
        flash(f'删除失败：{str(e)}', 'error')
    return redirect(url_for('admin_users'))

# 日志管理
@app.route('/admin/logs')
@login_required
def admin_logs():
    logs = Log.query.order_by(Log.log_time.desc()).all()
    return render_template('admin/logs.html', logs=logs)

@app.route('/admin/logs/add', methods=['GET', 'POST'])
@login_required
def admin_logs_add():
    if request.method == 'POST':
        try:
            log = Log(
                device_id=request.form.get('device_id'),
                operation_record=request.form.get('operation_record'),
                log_time=datetime.utcnow()
            )
            db.session.add(log)
            db.session.commit()
            flash('日志添加成功！', 'success')
            return redirect(url_for('admin_logs'))
        except Exception as e:
            flash(f'添加失败：{str(e)}', 'error')
    return render_template('admin/log_form.html', action='add')

@app.route('/admin/logs/edit/<int:log_id>', methods=['GET', 'POST'])
@login_required
def admin_logs_edit(log_id):
    log = Log.query.get_or_404(log_id)
    if request.method == 'POST':
        try:
            log.device_id = request.form.get('device_id')
            log.operation_record = request.form.get('operation_record')
            log.log_time = datetime.utcnow()
            db.session.commit()
            flash('日志更新成功！', 'success')
            return redirect(url_for('admin_logs'))
        except Exception as e:
            flash(f'更新失败：{str(e)}', 'error')
    return render_template('admin/log_form.html', action='edit', log=log)

@app.route('/admin/logs/delete/<int:log_id>')
@login_required
def admin_logs_delete(log_id):
    try:
        log = Log.query.get_or_404(log_id)
        db.session.delete(log)
        db.session.commit()
        flash('日志删除成功！', 'success')
    except Exception as e:
        flash(f'删除失败：{str(e)}', 'error')
    return redirect(url_for('admin_logs'))

# 视频管理
@app.route('/admin/videos')
@login_required
def admin_videos():
    videos = Video.query.all()
    return render_template('admin/videos.html', videos=videos)

@app.route('/admin/videos/add', methods=['GET', 'POST'])
@login_required
def admin_videos_add():
    if request.method == 'POST':
        try:
            video = Video(
                video_title=request.form.get('video_title'),
                video_link=request.form.get('video_link')
            )
            db.session.add(video)
            db.session.commit()
            flash('视频添加成功！', 'success')
            return redirect(url_for('admin_videos'))
        except Exception as e:
            flash(f'添加失败：{str(e)}', 'error')
    return render_template('admin/video_form.html', action='add')

@app.route('/admin/videos/edit/<int:video_id>', methods=['GET', 'POST'])
@login_required
def admin_videos_edit(video_id):
    video = Video.query.get_or_404(video_id)
    if request.method == 'POST':
        try:
            video.video_title = request.form.get('video_title')
            video.video_link = request.form.get('video_link')
            db.session.commit()
            flash('视频更新成功！', 'success')
            return redirect(url_for('admin_videos'))
        except Exception as e:
            flash(f'更新失败：{str(e)}', 'error')
    return render_template('admin/video_form.html', action='edit', video=video)

@app.route('/admin/videos/delete/<int:video_id>')
@login_required
def admin_videos_delete(video_id):
    try:
        video = Video.query.get_or_404(video_id)
        db.session.delete(video)
        db.session.commit()
        flash('视频删除成功！', 'success')
    except Exception as e:
        flash(f'删除失败：{str(e)}', 'error')
    return redirect(url_for('admin_videos'))

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
