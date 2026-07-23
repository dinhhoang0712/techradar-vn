// Reads a File as a base64 data URL. Shared by the avatar uploader and the post composer's
// image picker so both send the same {content_type, data_base64} shape to the backend.
export const fileToBase64 = (file: File): Promise<string> => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
});
