import React, { useState } from 'react';
import {
  HeartPulse,
  User,
  Lock,
  Mail,
  Phone,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  KeyRound,
  UserCheck,
  Eye,
  EyeOff
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/authApi';

export function LoginPage() {
  const { login, register } = useAuth();
  const [tab, setTab] = useState('login'); // 'login' | 'register'
  
  // Login form state - starts empty as a real web app
  const [loginUsername, setLoginUsername] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);

  // Register form state
  const [regFullName, setRegFullName] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regUsername, setRegUsername] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);

  // Forgot password modal state
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [forgotStep, setForgotStep] = useState(1); // 1: Email, 2: OTP + New Password
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotOtp, setForgotOtp] = useState('');
  const [forgotNewPass, setForgotNewPass] = useState('');
  const [showForgotNewPass, setShowForgotNewPass] = useState(false);
  const [otpSentHint, setOtpSentHint] = useState('');

  // UI state
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleLogin = async (e) => {
    e?.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');
    setIsSubmitting(true);
    try {
      await login(loginUsername.trim(), loginPassword);
    } catch (err) {
      setErrorMsg(err.message || 'Đăng nhập không thành công. Vui lòng kiểm tra lại tài khoản hoặc mật khẩu.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegister = async (e) => {
    e?.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');
    setIsSubmitting(true);
    try {
      await register({
        username: regUsername.trim(),
        password: regPassword,
        fullName: regFullName.trim(),
        email: regEmail.trim(),
        phoneNumber: regPhone.trim(),
      });
      setSuccessMsg('Đăng ký tài khoản thành công! Đang chuyển vào hệ thống...');
    } catch (err) {
      const msg = err.message || '';
      if (
        msg.includes('409') ||
        msg.toLowerCase().includes('đã được sử dụng') ||
        msg.toLowerCase().includes('đã tồn tại') ||
        msg.toLowerCase().includes('already exists')
      ) {
        setErrorMsg('Tên đăng nhập, email hoặc số điện thoại này đã được đăng ký trên hệ thống. Vui lòng kiểm tra lại hoặc chuyển sang tab Đăng nhập.');
      } else {
        setErrorMsg(err.message || 'Đăng ký tài khoản thất bại. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRequestOtp = async (e) => {
    e?.preventDefault();
    setErrorMsg('');
    setIsSubmitting(true);
    try {
      const resp = await authApi.forgotPassword(forgotEmail.trim());
      if (resp && resp.otp) {
        setOtpSentHint(`Mã OTP đã được tạo: ${resp.otp} (hoặc kiểm tra hòm thư MailHog :8025)`);
      } else {
        setOtpSentHint('Mã OTP đã được gửi đến email. Vui lòng kiểm tra hộp thư!');
      }
      setForgotStep(2);
    } catch (err) {
      setErrorMsg(err.message || 'Không tìm thấy tài khoản với email này.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetPassword = async (e) => {
    e?.preventDefault();
    setErrorMsg('');
    setIsSubmitting(true);
    try {
      await authApi.resetPassword(forgotEmail.trim(), forgotOtp.trim(), forgotNewPass);
      setSuccessMsg('Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới.');
      setShowForgotModal(false);
      setForgotStep(1);
      setLoginUsername(forgotEmail);
      setLoginPassword(forgotNewPass);
    } catch (err) {
      setErrorMsg(err.message || 'Mã OTP không chính xác hoặc đã hết hạn.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-page-wrapper">
      <div className="login-card">
        {/* Brand header */}
        <div className="login-brand">
          <div className="login-logo-circle">
            <HeartPulse size={36} color="#0d9488" />
          </div>
          <h1 className="login-title">FClinic Healthcare</h1>
          <p className="login-subtitle">Hệ thống Quản lý & Đặt lịch khám Bệnh viện</p>
        </div>

        {/* Tab switch */}
        <div className="login-tabs">
          <button
            type="button"
            className={`login-tab-btn ${tab === 'login' ? 'active' : ''}`}
            onClick={() => { setTab('login'); setErrorMsg(''); }}
          >
            Đăng nhập
          </button>
          <button
            type="button"
            className={`login-tab-btn ${tab === 'register' ? 'active' : ''}`}
            onClick={() => { setTab('register'); setErrorMsg(''); }}
          >
            Đăng ký tài khoản
          </button>
        </div>

        {/* Alerts */}
        {errorMsg && (
          <div className="login-alert error">
            <AlertCircle size={18} />
            <span>{errorMsg}</span>
          </div>
        )}
        {successMsg && (
          <div className="login-alert success">
            <CheckCircle2 size={18} />
            <span>{successMsg}</span>
          </div>
        )}

        {/* Tab Login Form */}
        {tab === 'login' ? (
          <form onSubmit={handleLogin} className="login-form">
            <div className="form-group">
              <label>Tên đăng nhập hoặc Email</label>
              <div className="input-with-icon">
                <User size={18} className="input-icon" />
                <input
                  type="text"
                  required
                  placeholder="Nhập tên đăng nhập hoặc email..."
                  value={loginUsername}
                  onChange={(e) => setLoginUsername(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <div className="form-label-row">
                <label>Mật khẩu</label>
                <button
                  type="button"
                  tabIndex={-1}
                  className="link-forgot"
                  onClick={() => { setShowForgotModal(true); setErrorMsg(''); }}
                >
                  Quên mật khẩu?
                </button>
              </div>
              <div className="input-with-icon">
                <Lock size={18} className="input-icon" />
                <input
                  type={showLoginPassword ? 'text' : 'password'}
                  required
                  placeholder="Nhập mật khẩu..."
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="has-toggle"
                />
                <button
                  type="button"
                  tabIndex={-1}
                  className="password-toggle-btn"
                  onClick={() => setShowLoginPassword((prev) => !prev)}
                  aria-label={showLoginPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  title={showLoginPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showLoginPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-submit-login"
            >
              {isSubmitting ? 'Đang xác thực...' : 'Đăng nhập vào hệ thống'}
              <ArrowRight size={18} />
            </button>
          </form>
        ) : (
          /* Tab Register Form */
          <form onSubmit={handleRegister} className="login-form">
            <div className="form-group">
              <label>Họ và tên bệnh nhân</label>
              <div className="input-with-icon">
                <UserCheck size={18} className="input-icon" />
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Nguyễn Văn An"
                  value={regFullName}
                  onChange={(e) => setRegFullName(e.target.value)}
                />
              </div>
            </div>

            <div className="form-row-2">
              <div className="form-group">
                <label>Số điện thoại</label>
                <div className="input-with-icon">
                  <Phone size={18} className="input-icon" />
                  <input
                    type="tel"
                    required
                    placeholder="0912345678"
                    value={regPhone}
                    onChange={(e) => setRegPhone(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Email</label>
                <div className="input-with-icon">
                  <Mail size={18} className="input-icon" />
                  <input
                    type="email"
                    required
                    placeholder="name@example.com"
                    value={regEmail}
                    onChange={(e) => setRegEmail(e.target.value)}
                  />
                </div>
              </div>
            </div>

            <div className="form-row-2">
              <div className="form-group">
                <label>Tên đăng nhập</label>
                <div className="input-with-icon">
                  <User size={18} className="input-icon" />
                  <input
                    type="text"
                    required
                    placeholder="username..."
                    value={regUsername}
                    onChange={(e) => setRegUsername(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Mật khẩu</label>
                <div className="input-with-icon">
                  <Lock size={18} className="input-icon" />
                  <input
                    type={showRegPassword ? 'text' : 'password'}
                    required
                    placeholder="Tối thiểu 6 ký tự"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    className="has-toggle"
                  />
                  <button
                    type="button"
                    tabIndex={-1}
                    className="password-toggle-btn"
                    onClick={() => setShowRegPassword((prev) => !prev)}
                    aria-label={showRegPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    title={showRegPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showRegPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-submit-login"
            >
              {isSubmitting ? 'Đang tạo tài khoản...' : 'Đăng ký tài khoản Bệnh nhân'}
              <ArrowRight size={18} />
            </button>
          </form>
        )}
      </div>

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div className="modal-backdrop">
          <div className="forgot-modal">
            <div className="modal-header">
              <div className="modal-icon-badge">
                <KeyRound size={22} color="#0d9488" />
              </div>
              <h3>Khôi phục mật khẩu</h3>
              <p>Hệ thống sẽ gửi mã xác thực OTP 6 chữ số đến email của bạn</p>
            </div>

            {forgotStep === 1 ? (
              <form onSubmit={handleRequestOtp} className="modal-form">
                <div className="form-group">
                  <label>Email đã đăng ký</label>
                  <div className="input-with-icon">
                    <Mail size={18} className="input-icon" />
                    <input
                      type="email"
                      required
                      placeholder="patient@fclinic.vn"
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                    />
                  </div>
                </div>
                <div className="modal-actions">
                  <button
                    type="button"
                    className="btn-cancel"
                    onClick={() => setShowForgotModal(false)}
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="btn-confirm"
                  >
                    {isSubmitting ? 'Đang gửi...' : 'Gửi mã OTP'}
                  </button>
                </div>
              </form>
            ) : (
              <form onSubmit={handleResetPassword} className="modal-form">
                {otpSentHint && (
                  <div className="otp-hint-box">
                    <CheckCircle2 size={16} />
                    <span>{otpSentHint}</span>
                  </div>
                )}
                <div className="form-group">
                  <label>Mã OTP (6 chữ số)</label>
                  <input
                    type="text"
                    required
                    maxLength={6}
                    placeholder="123456"
                    className="otp-input"
                    value={forgotOtp}
                    onChange={(e) => setForgotOtp(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label>Mật khẩu mới</label>
                  <div className="input-with-icon">
                    <Lock size={18} className="input-icon" />
                    <input
                      type={showForgotNewPass ? 'text' : 'password'}
                      required
                      placeholder="Nhập mật khẩu mới..."
                      value={forgotNewPass}
                      onChange={(e) => setForgotNewPass(e.target.value)}
                      className="has-toggle"
                    />
                    <button
                      type="button"
                      tabIndex={-1}
                      className="password-toggle-btn"
                      onClick={() => setShowForgotNewPass((prev) => !prev)}
                      aria-label={showForgotNewPass ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      title={showForgotNewPass ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    >
                      {showForgotNewPass ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>
                <div className="modal-actions">
                  <button
                    type="button"
                    className="btn-cancel"
                    onClick={() => setForgotStep(1)}
                  >
                    Quay lại
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="btn-confirm"
                  >
                    {isSubmitting ? 'Đang cập nhật...' : 'Xác nhận đổi mật khẩu'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

