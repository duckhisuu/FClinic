import React, { useState } from 'react';
import { ArrowUpRight, Plus, Search, UsersRound, ListFilter } from 'lucide-react';
import { dateText, initials, phone, shortId } from '../model';
import { usePatients } from '../hooks/usePatients';
import { PageHeading, PageState, Pagination, StatusBadge } from '../components/Shared';
export function PatientListPage({ api, revision, navigate, canCreate }) {
  const [params, setParams] = useState({ page: 0, size: 10, sort: 'fullName,asc' });
  const [search, setSearch] = useState(''); const [searchBy, setSearchBy] = useState('keyword'); const [issue, setIssue] = useState('');
  const resource = usePatients(api, params, revision); const data = resource.data;
  function submit(e) {
    e.preventDefault(); let value = search.trim();
    if (searchBy === 'phoneNumber' && value && !phone(value)) { setIssue('Vui lòng nhập số điện thoại đầy đủ, hợp lệ.'); return; }
    if (value.length > 255) { setIssue('Nội dung tìm kiếm tối đa 255 ký tự.'); return; }
    if (searchBy === 'phoneNumber' && value) value = phone(value);
    setIssue(''); setParams(p => ({ page: 0, size: p.size, sort: p.sort, ...(value ? { [searchBy]: value } : {}) }));
  }
  return <>
    <PageHeading title="Quản lý bệnh nhân" description="Tra cứu hồ sơ, cập nhật thông tin và theo dõi bệnh án." action={<button className="pt-button pt-primary" disabled={!canCreate} title={!canCreate ? 'Cần quyền tạo hồ sơ được xác minh' : undefined} onClick={() => navigate({ name: 'createPatient' })}><Plus size={18} />Tạo bệnh nhân</button>} />
    <p className="pt-filter-caption">Danh sách ban đầu gồm hồ sơ trong phạm vi đã tiếp nhận. Với bệnh nhân đã có tài khoản hoặc hồ sơ, hãy tìm theo tên, mã hồ sơ hoặc số điện thoại và mở hồ sơ hiện có; không cần tạo lại.</p>
    <div className="pt-summary"><span className="pt-icon-tile"><UsersRound size={24} /></span><div><span>Hồ sơ khớp tìm kiếm</span><strong>{data ? data.totalElements.toLocaleString('vi-VN') : '—'}</strong></div><div className="pt-summary-note"><span className="pt-dot" />Hồ sơ hành chính & bệnh án được quản lý riêng</div></div>
    <section className="pt-card">
      <div className="pt-toolbar"><form onSubmit={submit} className="pt-search" noValidate><label className="pt-sr" htmlFor="pt-search-by">Tìm theo</label><select id="pt-search-by" value={searchBy} onChange={e => { setSearchBy(e.target.value); setIssue(''); }}><option value="keyword">Họ và tên</option><option value="phoneNumber">Điện thoại</option></select><div><Search size={18} /><input aria-label="Tìm bệnh nhân" placeholder={searchBy === 'keyword' ? 'Nhập tên bệnh nhân…' : 'Nhập số điện thoại đầy đủ…'} value={search} onChange={e => setSearch(e.target.value)} /></div><button className="pt-button pt-secondary" type="submit">Tìm kiếm</button></form><label className="pt-sort"><ListFilter size={16} /><span className="pt-sr">Sắp xếp bệnh nhân</span><select aria-label="Sắp xếp bệnh nhân" value={params.sort} onChange={e => setParams(p => ({ ...p, page: 0, sort: e.target.value }))}><option value="fullName,asc">Tên A → Z</option><option value="fullName,desc">Tên Z → A</option><option value="createdAt,desc">Mới tạo gần đây</option><option value="createdAt,asc">Tạo sớm nhất</option></select></label></div>
      {issue && <p className="pt-field-error pt-pad" role="alert">{issue}</p>}
      {(params.keyword || params.phoneNumber) && <div className="pt-filter-caption">Kết quả cho “{params.keyword || params.phoneNumber}”<button className="pt-link" onClick={() => { setSearch(''); setParams(p => ({ page: 0, size: p.size, sort: p.sort })); }}>Xóa tìm kiếm</button></div>}
      <PageState resource={resource} empty={data?.content.length === 0} emptyTitle="Không tìm thấy bệnh nhân" emptyText="Thử thay đổi nội dung tìm kiếm hoặc tạo hồ sơ mới nếu đã được cấp quyền." action={<button className="pt-button pt-secondary" disabled={!canCreate} onClick={() => navigate({ name: 'createPatient' })}><Plus size={16} />Tạo hồ sơ mới</button>}>
        {data && <div className="pt-table-wrap"><table className="pt-table"><thead><tr><th>Bệnh nhân</th><th>Mã bệnh nhân</th><th>Điện thoại</th><th>Ngày sinh</th><th>Trạng thái</th><th><span className="pt-sr">Thao tác</span></th></tr></thead><tbody>{data.content.map(p => <tr key={p.id}><td><button className="pt-patient-link" onClick={() => navigate({ name: 'profile', patientId: p.id })}><span className={`pt-avatar ${p.gender === 'FEMALE' ? 'pt-avatar-lilac' : ''}`}>{initials(p.fullName)}</span><span><strong>{p.fullName}</strong><small>{p.email || 'Chưa có email'}</small></span></button></td><td><span className="pt-code" title={p.patientCode}>PAT-{shortId(p.id)}</span></td><td>{p.phoneNumber}</td><td>{dateText(p.dateOfBirth)}</td><td><StatusBadge status={p.status} /></td><td><button className="pt-icon-button" aria-label={`Xem hồ sơ ${p.fullName}`} onClick={() => navigate({ name: 'profile', patientId: p.id })}><ArrowUpRight size={18} /></button></td></tr>)}</tbody></table></div>}
      </PageState>
      <Pagination data={data} onPage={page => setParams(p => ({ ...p, page }))} onSize={size => setParams(p => ({ ...p, size, page: 0 }))} />
    </section>
  </>;
}
