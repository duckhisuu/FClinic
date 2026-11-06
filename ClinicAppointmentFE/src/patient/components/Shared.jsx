import React from 'react';
import { AlertCircle, ArrowLeft, ChevronLeft, ChevronRight, FileSearch, RefreshCw, ShieldAlert } from 'lucide-react';
import { statuses } from '../model';
export function StatusBadge({ status }) { return <span className={`pt-badge ${status === 'ACTIVE' ? 'pt-active' : 'pt-inactive'}`}><span />{statuses[status] || status}</span>; }
export function PageHeading({ eyebrow, title, description, action, onBack }) { return <header className="pt-heading"><div>{onBack && <button className="pt-link pt-back" onClick={onBack}><ArrowLeft size={16} />Quay lại</button>}<p className="pt-eyebrow">{eyebrow || 'HỒ SƠ & CHĂM SÓC'}</p><h1 tabIndex={-1}>{title}</h1>{description && <p className="pt-description">{description}</p>}</div>{action && <div className="pt-heading-action">{action}</div>}</header>; }
export function ErrorNotice({ error, onRetry, onReload }) {
  if (!error) return null;
  const conflict = error.code === 'VERSION_CONFLICT';
  return <div className="pt-notice pt-error" role="alert"><AlertCircle size={20} /><div><strong>{conflict ? 'Hồ sơ đã được cập nhật ở nơi khác' : error.uncertain ? 'Chưa xác định kết quả lưu' : 'Chưa thể hoàn tất thao tác'}</strong><p>{error.uncertain ? 'Yêu cầu có thể đã đến máy chủ. Kiểm tra lại dữ liệu trước khi gửi thêm; giao diện chưa xác nhận lưu thành công.' : error.message || 'Vui lòng thử lại.'}</p>{conflict && <p>Tải lại bản mới nhất trước khi tiếp tục. Nội dung đang nhập chưa được lưu.</p>}{(onReload && (conflict || error.uncertain)) ? <button className="pt-link" onClick={onReload}><RefreshCw size={15} />Tải lại dữ liệu</button> : onRetry ? <button className="pt-link" onClick={onRetry}><RefreshCw size={15} />Thử lại</button> : null}</div></div>;
}
export function Blocker({ children }) { return <div className="pt-notice pt-warning" role="status"><ShieldAlert size={20} /><div><strong>Chưa sẵn sàng trên hệ thống thật</strong><p>{children}</p></div></div>; }
export function PageState({ resource, empty, emptyTitle = 'Chưa có dữ liệu', emptyText = 'Thông tin sẽ xuất hiện tại đây khi có dữ liệu phù hợp.', action, children }) {
  if (resource.loading) return <div className="pt-skeleton" role="status" aria-label="Đang tải dữ liệu"><div /><div /><div /><div /><span className="pt-sr">Đang tải dữ liệu…</span></div>;
  if (resource.error) return <ErrorNotice error={resource.error} onRetry={resource.reload} />;
  if (empty) return <div className="pt-empty"><span className="pt-empty-icon"><FileSearch size={28} /></span><h2>{emptyTitle}</h2><p>{emptyText}</p>{action}</div>;
  return children;
}
export function Pagination({ data, onPage, onSize }) {
  if (!data) return null;
  const start = data.totalElements ? data.page * data.size + 1 : 0;
  return <div className="pt-pagination"><p>Hiển thị <strong>{start}–{Math.min((data.page + 1) * data.size, data.totalElements)}</strong> / {data.totalElements} kết quả</p><div><label>Số dòng<select aria-label="Số dòng mỗi trang" value={data.size} onChange={e => onSize(Number(e.target.value))}><option value={5}>5</option><option value={10}>10</option><option value={20}>20</option></select></label><button className="pt-icon-button" aria-label="Trang trước" disabled={data.page === 0} onClick={() => onPage(data.page - 1)}><ChevronLeft size={17} /></button><span className="pt-page-number">{data.page + 1} / {Math.max(1, data.totalPages)}</span><button className="pt-icon-button" aria-label="Trang sau" disabled={data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)}><ChevronRight size={17} /></button></div></div>;
}
export function Field({ id, label, required, issue, hint, children, wide }) { return <div className={`pt-field ${wide ? 'pt-wide' : ''}`}><label htmlFor={id}>{label}{required && <span aria-hidden="true"> *</span>}</label>{React.cloneElement(children, { id, 'aria-invalid': !!issue, 'aria-describedby': issue ? `${id}-error` : hint ? `${id}-hint` : undefined })}{issue ? <span id={`${id}-error`} className="pt-field-error">{issue}</span> : hint && <small id={`${id}-hint`}>{hint}</small>}</div>; }
export function MetaGrid({ entries }) { return <dl className="pt-meta-grid">{entries.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || 'Chưa cập nhật'}</dd></div>)}</dl>; }
