// Chụp 1 element DOM thành PDF nhiều trang A4 — dùng chung cho ReportPage (báo cáo) và
// InterviewPage (kết quả phỏng vấn thử). scale 1.5 + nén JPEG (thay vì PNG mặc định của
// html2canvas) vì nội dung chủ yếu là chữ, PNG ở scale cao cho ra file nặng không cần thiết.
export async function exportElementToPdf(el: HTMLElement, filename: string): Promise<void> {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
        import('html2canvas'),
        import('jspdf'),
    ]);
    const canvas = await html2canvas(el, { backgroundColor: '#060810', scale: 1.5 });
    const pdf = new jsPDF('p', 'mm', 'a4');
    const pageWidth = pdf.internal.pageSize.getWidth();
    const pageHeight = pdf.internal.pageSize.getHeight();
    const imgWidth = pageWidth;
    const imgHeight = (canvas.height * imgWidth) / canvas.width;
    const imgData = canvas.toDataURL('image/jpeg', 0.85);

    let heightLeft = imgHeight;
    let position = 0;
    pdf.addImage(imgData, 'JPEG', 0, position, imgWidth, imgHeight);
    heightLeft -= pageHeight;

    while (heightLeft > 0) {
        position -= pageHeight;
        pdf.addPage();
        pdf.addImage(imgData, 'JPEG', 0, position, imgWidth, imgHeight);
        heightLeft -= pageHeight;
    }

    pdf.save(filename);
}
