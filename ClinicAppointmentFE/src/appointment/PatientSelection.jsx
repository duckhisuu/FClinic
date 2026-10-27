import React, { useState, useEffect } from "react";
import { UserRound, Search, ChevronRight, Check } from "lucide-react";
import { patientApi } from "./api";
import { ErrorNotice, Busy } from "./ui";

// Reads existing Patient profiles only. Does not create accounts, profiles, or permissions.
export function PatientSelection({ patientId, selectedPatient, onSelect }) {
  const [patient, setPatient] = useState(selectedPatient || null),
    [query, setQuery] = useState(""),
    [candidates, setCandidates] = useState(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!patientId) return;
    const c = new AbortController();
    setBusy(true);
    patientApi
      .get(patientId, c.signal)
      .then((p) => {
        setPatient(p);
        onSelect(p);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [patientId]);
  const search = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const phone = /^[+0-9 ()-]+$/.test(query.trim());
      setCandidates(
        await patientApi.search({
          [phone ? "phoneNumber" : "keyword"]: query.trim(),
          page: 0,
          size: 20,
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="panel">
      <div className="panel-title">
        <UserRound size={20} />
        <h2>Người đến khám</h2>
      </div>
      <ErrorNotice error={error} />
      {busy && <Busy />}
      {patient && (
        <div className="selected-patient">
          <span className="avatar small">
            <UserRound size={21} />
          </span>
          <div>
            <strong>{patient.fullName}</strong>
            <small>{patient.patientCode}</small>
            <span>
              {patient.phoneNumber}
              {patient.dateOfBirth ? ` · Sinh ${patient.dateOfBirth}` : ""}
            </span>
            <b className={`badge ${patient.status}`}>
              {patient.status === "ACTIVE"
                ? "Đang hoạt động"
                : "Ngừng hoạt động"}
            </b>
          </div>
        </div>
      )}
      {!patientId && (
        <>
          <form className="inline-form" onSubmit={search}>
            <input
              aria-label="Tìm bệnh nhân"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tên, mã bệnh nhân hoặc số điện thoại"
              required
            />
            <button className="button secondary" disabled={busy}>
              <Search size={16} />
              Tìm hồ sơ
            </button>
          </form>
          <p className="hint">
            Một số điện thoại có thể dùng cho nhiều người. Đối chiếu mã hồ sơ và
            ngày sinh trước khi chọn.
          </p>
          {candidates?.content?.map((p) => (
            <button
              type="button"
              key={p.id}
              className="candidate"
              onClick={() => {
                setPatient(p);
                onSelect(p);
              }}
            >
              <span>
                <strong>{p.fullName}</strong>
                <small>
                  {p.patientCode} · {p.dateOfBirth || "Chưa có ngày sinh"} ·{" "}
                  {p.phoneNumber}
                </small>
              </span>
              {patient?.id === p.id ? (
                <Check size={19} />
              ) : (
                <ChevronRight size={19} />
              )}
            </button>
          ))}
          {candidates && !candidates.content.length && (
            <p>Không có hồ sơ khớp tìm kiếm.</p>
          )}
          {candidates?.totalElements > 20 && (
            <p className="hint">
              Có nhiều kết quả. Thêm tên hoặc mã hồ sơ để thu hẹp tìm kiếm.
            </p>
          )}
        </>
      )}
    </section>
  );
}
