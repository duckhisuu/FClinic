import React, { useEffect, useRef, useState } from 'react';
import { doctorApi, appointmentApi, ApiError } from '../appointment/api';
import { today, dayText, shift, Busy, Empty, ErrorNotice } from '../appointment/ui';

// Doctor owns offers; Appointment owns occupancy. This screen merges those facts for the logged-in Doctor.
export function DoctorSchedulePage({ doctorId }) {
  const [anchor, setAnchor] = useState(today), [period, setPeriod] = useState('WEEK');
  const [revision, setRevision] = useState(0), [data, setData] = useState(null);
  const [error, setError] = useState(null), [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [slotDate, setSlotDate] = useState(today), [hour, setHour] = useState('08'), [notice, setNotice] = useState('');
  const [now, setNow] = useState(Date.now);
  const running = useRef(false);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(null); setData(null);
    if (!doctorId) { setLoading(false); return () => controller.abort(); }
    (async () => {
      const schedule = await doctorApi.getDoctorSchedule(doctorId, period, anchor, controller.signal);
      if (schedule.doctorId !== doctorId || !Array.isArray(schedule.days))
        throw new ApiError('Lịch làm việc không hợp lệ.', 503, 'DEPENDENCY_UNAVAILABLE');
      const days = await Promise.all(schedule.days.map(async day => {
        // At most 24 confirmed hourly appointments can occupy a day.
        const page = await appointmentApi.list({ doctorId, date: day.date, status: 'CONFIRMED', page: 0, size: 100 }, controller.signal);
        if (!Array.isArray(page.content) || page.totalElements > 100
            || page.content.some(a => a.doctorId !== doctorId || a.appointmentDate !== day.date || a.status !== 'CONFIRMED'))
          throw new ApiError('Không thể xác minh trạng thái lịch hẹn.', 503, 'DEPENDENCY_UNAVAILABLE');
        return { ...day, occupied: new Set(page.content.map(a => a.startTime)) };
      }));
      if (!controller.signal.aborted) setData({ ...schedule, days });
    })().catch(err => { if (!controller.signal.aborted) setError(err); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [doctorId, anchor, period, revision]);
  const move = direction => {
    if (period === 'WEEK') setAnchor(value => shift(value, direction * 7));
    else setAnchor(value => {
      const date = new Date(`${value.slice(0, 7)}-01T12:00:00Z`);
      date.setUTCMonth(date.getUTCMonth() + direction); return date.toISOString().slice(0, 10);
    });
  };
  const create = async event => {
    event.preventDefault(); if (running.current || !doctorId) return;
    running.current = true; setBusy(true); setError(null); setNotice('');
    try {
      await doctorApi.createDoctorSlot(doctorId, { slotDate, startTime: `${hour}:00:00`, endTime: `${String(Number(hour) + 1).padStart(2, '0')}:00:00` });
      setAnchor(slotDate); setRevision(value => value + 1); setNotice('Đã thêm ca khám 60 phút.');
    } catch (err) { setError(err); }
    finally { running.current = false; setBusy(false); }
  };
  if (!doctorId) return <Empty title="Tài khoản chưa liên kết bác sĩ" />;
  return <>
    <div className="page-heading"><div><h1>Lịch làm việc của tôi</h1><p>Ca đã công bố và lịch hẹn thực tế · Giờ Việt Nam</p></div>
      <button className="button secondary" onClick={() => setRevision(value => value + 1)}>Tải lại lịch</button></div>
    <section className="panel">
      <div className="button-row">
        <button className="button secondary" onClick={() => move(-1)}>Khoảng trước</button>
        <label>Khoảng xem<select aria-label="Khoảng xem" value={period} onChange={e => setPeriod(e.target.value)}><option value="WEEK">Tuần</option><option value="MONTH">Tháng</option></select></label>
        <label>Ngày xem<input aria-label="Ngày xem lịch" type="date" value={anchor} onChange={e => e.target.value && setAnchor(e.target.value)} /></label>
        <button className="button secondary" onClick={() => move(1)}>Khoảng tiếp</button>
      </div>
      <ErrorNotice error={error} />
      {notice && <p role="status" className="notice success">{notice}</p>}
      {loading ? <Busy text="Đang đối chiếu lịch hẹn…" /> : data && <>
        <p>{dayText(data.fromDate)} – {dayText(data.toDate)}</p>
        {!data.days.length && <Empty title="Chưa có ca khám trong khoảng này" />}
        {data.days.map(day => <section key={day.date} className="panel">
          <h2>{dayText(day.date)}</h2><div className="slot-grid">
            {day.slots.map(slot => {
              const occupied = day.occupied.has(slot.startTime);
              const past = new Date(`${day.date}T${slot.startTime}+07:00`).getTime() <= now;
              const label = occupied ? 'Đã có lịch hẹn' : slot.published === false ? 'Chưa công bố' : past ? 'Đã qua giờ' : 'Còn trống';
              return <div className="slot" key={slot.id}><b>{slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}</b><span>{label}</span></div>;
            })}
          </div>
        </section>)}
      </>}
    </section>
    <section className="panel"><h2>Thêm ca khám</h2>
      <form onSubmit={create} className="button-row">
        <label>Ngày khám<input aria-label="Ngày tạo ca" type="date" min={today()} value={slotDate} required disabled={busy} onChange={e => setSlotDate(e.target.value)} /></label>
        <label>Giờ bắt đầu<select aria-label="Giờ bắt đầu ca" value={hour} disabled={busy} onChange={e => setHour(e.target.value)}>
          {Array.from({ length: 23 }, (_, h) => String(h).padStart(2, '0')).map(h => <option key={h} value={h}>{h}:00–{String(Number(h) + 1).padStart(2, '0')}:00</option>)}
        </select></label>
        <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Thêm ca 60 phút'}</button>
      </form>
    </section>
  </>;
}
export default DoctorSchedulePage;