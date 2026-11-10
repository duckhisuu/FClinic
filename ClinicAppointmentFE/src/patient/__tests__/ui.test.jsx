import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientModule } from './PreviewHarness';
import { PatientForm } from '../components/PatientForm';
import { MedicalRecordForm } from '../components/MedicalRecordForm';
import { PatientApiError } from '../api/patientApi';
import { makeFixtures } from '../preview/fixtures';
beforeEach(() => { vi.spyOn(window, 'confirm').mockReturnValue(true); });
function preview() { window.history.replaceState({}, '', '/?patientPreview=1'); const fetch = vi.fn(); vi.stubGlobal('fetch', fetch); render(<PatientModule />); return { user: userEvent.setup(), fetch }; }
async function selectPatient(user) {
  await screen.findByText('Bùi Thanh Mai');
  await user.type(screen.getByRole('textbox', { name: 'Tìm bệnh nhân' }), 'Nguyễn Minh An');
  await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
  await user.click(await screen.findByRole('button', { name: 'Xem hồ sơ Nguyễn Minh An' }));
  await screen.findByRole('heading', { name: 'Nguyễn Minh An' });
}
describe('complete preview screens', () => {
  it('MH01 search, pagination and error/empty/loading scenarios work without fetch', async () => {
    const { user, fetch } = preview(); await screen.findByText('Bùi Thanh Mai');
    expect(screen.getByText('UI Preview — Dữ liệu minh họa, không lưu vào hệ thống thật.')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Trang sau' })); await waitFor(() => expect(screen.getByText('2 / 3')).toBeVisible());
    await user.selectOptions(screen.getByRole('combobox', { name: 'Số dòng mỗi trang' }), '5'); await waitFor(() => expect(screen.getByText('1 / 5')).toBeVisible());
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tình huống Preview' }), 'empty'); await screen.findByText('Không tìm thấy bệnh nhân');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tình huống Preview' }), 'error'); await screen.findByRole('alert'); expect(screen.getByRole('button', { name: 'Thử lại' })).toBeVisible();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tình huống Preview' }), 'slow'); expect(screen.getByLabelText('Đang tải dữ liệu')).toBeVisible();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Tình huống Preview' }), 'normal'); await screen.findByText('Bùi Thanh Mai'); expect(fetch).not.toHaveBeenCalled();
  });
  it('MH02 creates, reads and replaces a synthetic profile using server-returned version', async () => {
    const { user, fetch } = preview(); await screen.findByText('Bùi Thanh Mai');
    await user.click(screen.getByRole('button', { name: 'Tạo bệnh nhân' }));
    await user.click(screen.getByRole('button', { name: 'Tạo hồ sơ', exact: true })); expect(screen.getByText('Vui lòng kiểm tra các trường được đánh dấu.')).toBeVisible();
    await user.type(screen.getByLabelText(/Họ và tên/), 'Bệnh nhân UI tổng hợp'); await user.type(screen.getByLabelText(/Số điện thoại/), '0900000099');
    await user.click(screen.getByRole('button', { name: 'Tạo hồ sơ', exact: true })); await screen.findByRole('heading', { name: 'Bệnh nhân UI tổng hợp' });
    await user.click(screen.getByRole('button', { name: 'Chỉnh sửa hồ sơ' })); await screen.findByDisplayValue('Bệnh nhân UI tổng hợp');
    await user.clear(screen.getByLabelText(/Họ và tên/)); await user.type(screen.getByLabelText(/Họ và tên/), 'Bệnh nhân UI đã sửa');
    await user.click(screen.getByRole('button', { name: 'Lưu cập nhật' })); await screen.findByRole('heading', { name: 'Bệnh nhân UI đã sửa' });
    expect(screen.getByText('Version').parentElement).toHaveTextContent('1'); expect(fetch).not.toHaveBeenCalled();
  });
  it('MH03/MH04 list/detail/create/edit medical records without Doctor or Appointment calls', async () => {
    const { user, fetch } = preview(); await selectPatient(user);
    await user.click(screen.getByRole('button', { name: 'Xem bệnh án', exact: true })); await screen.findByText('14');
    await user.click(screen.getByRole('button', { name: 'Trang sau' })); await waitFor(() => expect(screen.getByText('2 / 2')).toBeVisible());
    await user.click(screen.getByRole('button', { name: 'Trang trước' }));
    const open = await screen.findAllByRole('button', { name: /^Xem bệnh án / }); await user.click(open[0]);
    await screen.findByRole('heading', { name: 'Nội dung lần khám' }); expect(screen.getByText('Doctor UUID (Patient v3)')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Chỉnh sửa bệnh án' })); await screen.findByLabelText(/Lý do chỉnh sửa/);
    await user.click(screen.getByRole('button', { name: 'Lưu bệnh án' })); expect(screen.getByText('Vui lòng kiểm tra các trường được đánh dấu.')).toBeVisible();
    await user.type(screen.getByLabelText(/Lý do chỉnh sửa/), 'Sửa dữ liệu minh họa'); await user.clear(screen.getByLabelText(/Chẩn đoán/)); await user.type(screen.getByLabelText(/Chẩn đoán/), 'Chẩn đoán UI đã sửa');
    await user.click(screen.getByRole('button', { name: 'Lưu bệnh án' })); await screen.findByText('Mô phỏng: Đã cập nhật bệnh án.'); await screen.findByRole('heading', { name: 'Nội dung lần khám' });
    await user.click(screen.getByRole('button', { name: 'Quay lại' })); await screen.findByRole('heading', { name: 'Các lần khám' });
    await user.click(screen.getByRole('button', { name: 'Tạo bệnh án' })); await screen.findByLabelText(/Triệu chứng/);
    await user.type(screen.getByLabelText(/Triệu chứng/), 'Triệu chứng synthetic'); await user.type(screen.getByLabelText(/Chẩn đoán/), 'Lần khám UI mới');
    await user.click(screen.getByRole('button', { name: 'Tạo bệnh án', exact: true })); await screen.findByText('Mô phỏng: Đã tạo bệnh án.'); await screen.findByRole('heading', { name: 'Nội dung lần khám' }); expect(screen.getByText('Lần khám UI mới')).toBeVisible(); expect(fetch).not.toHaveBeenCalled();
  });
  it('switching Live discards visible preview responses and does not create a fake session', async () => {
    const { user, fetch } = preview(); await screen.findByText('Bùi Thanh Mai');
    await user.click(screen.getByRole('button', { name: 'Live API', exact: true })); await screen.findByRole('alert');
    expect(screen.queryByText('Bùi Thanh Mai')).not.toBeInTheDocument(); expect(screen.queryByRole('button', { name: 'Tạo bệnh nhân' })).not.toBeInTheDocument(); expect(fetch).not.toHaveBeenCalled();
  });
});
describe('editor boundaries', () => {
  it('Live clinical mutation is disabled even with a populated valid form', async () => {
    const onSave = vi.fn(); const { patients: [patient], records: [record] } = makeFixtures();
    render(<MedicalRecordForm patient={patient} record={record} preview={false} onSave={onSave} />);
    expect(screen.getByRole('button', { name: 'Lưu bệnh án' })).toBeDisabled(); expect(screen.getByLabelText(/Doctor UUID/)).toBeDisabled();
    fireEvent.submit(screen.getByRole('button', { name: 'Lưu bệnh án' }).closest('form')); expect(onSave).not.toHaveBeenCalled();
  });
  it('locks duplicate submits and exposes conflict reload without inventing a version', async () => {
    let reject; const onSave = vi.fn(() => new Promise((_, r) => { reject = r; })); const onReload = vi.fn(); const patient = makeFixtures().patients[0];
    render(<PatientForm patient={patient} allowed preview onSave={onSave} onReload={onReload} />);
    const form = screen.getByRole('button', { name: 'Lưu cập nhật' }).closest('form'); fireEvent.submit(form); fireEvent.submit(form); expect(onSave).toHaveBeenCalledTimes(1);
    await act(async () => reject(new PatientApiError(409, 'VERSION_CONFLICT')));
    expect(screen.getByRole('alert')).toHaveTextContent('Hồ sơ đã được cập nhật ở nơi khác'); await userEvent.click(screen.getByRole('button', { name: 'Tải lại dữ liệu' })); expect(onReload).toHaveBeenCalledTimes(1);
  });
});
