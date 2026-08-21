/**
 * Upload — 统一文件上传模块
 *
 * 自动判断文件大小：
 *   ≤ 50MB → 单次上传
 *   > 50MB → 分片上传（每片 10MB）
 *
 * 使用：
 *   Upload.file(file, targetPath).then(function(result) {
 *       // result: { success, id, url }
 *   });
 */
var Upload = (function () {

    var CHUNK_SIZE = 10 * 1024 * 1024;  // 10MB
    var SINGLE_LIMIT = 50 * 1024 * 1024; // 50MB
    var BASE = '/api/upload';

    /**
     * 上传文件（自动选择单次/分片）
     * @param {File} file       文件对象
     * @param {string} targetPath  RustFS 存储路径（含文件名），如 "home/games/xxx.jpg"
     * @param {function} onProgress  进度回调 onProgress(percent)，0-100
     * @returns {Promise<{success:boolean, id:string, url:string}>}
     */
    function upload(file, targetPath, onProgress) {
        if (!file) return Promise.reject(new Error('文件为空'));
        if (file.size <= SINGLE_LIMIT) {
            return uploadSingle(file, targetPath, onProgress);
        } else {
            return uploadChunked(file, targetPath, onProgress);
        }
    }

    // ======================== 单次上传 ========================

    function uploadSingle(file, targetPath, onProgress) {
        var fd = new FormData();
        fd.append('file', file);
        fd.append('targetPath', targetPath);

        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', BASE + '/single');

            var token = localStorage.getItem('token');
            if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);

            xhr.upload.onprogress = function (e) {
                if (e.lengthComputable && onProgress) {
                    onProgress(Math.round(e.loaded / e.total * 100));
                }
            };

            xhr.onload = function () {
                try {
                    var d = JSON.parse(xhr.responseText);
                    if (xhr.status === 200 && d.success) {
                        resolve(d);
                    } else {
                        reject(new Error(d.error || '上传失败 (' + xhr.status + ')'));
                    }
                } catch (e) {
                    reject(new Error('响应解析失败'));
                }
            };

            xhr.onerror = function () { reject(new Error('网络错误')); };
            xhr.send(fd);
        });
    }

    // ======================== 分片上传 ========================

    function uploadChunked(file, targetPath, onProgress) {
        var totalChunks = Math.ceil(file.size / CHUNK_SIZE);
        var uploadedBytes = 0;

        // Step 1: 初始化会话
        return postJson(BASE + '/chunk/init', {
            totalChunks: totalChunks,
            targetPath: targetPath,
            fileName: file.name
        }).then(function (initRes) {
            if (!initRes.success) throw new Error(initRes.error || '初始化失败');
            var uploadId = initRes.uploadId;

            // Step 2: 顺序上传每个分片
            var chain = Promise.resolve();
            for (var i = 0; i < totalChunks; i++) {
                (function (idx) {
                    chain = chain.then(function () {
                        var start = idx * CHUNK_SIZE;
                        var end = Math.min(start + CHUNK_SIZE, file.size);
                        var chunk = file.slice(start, end);

                        var fd = new FormData();
                        fd.append('file', chunk, file.name + '.part' + idx);
                        fd.append('uploadId', uploadId);
                        fd.append('chunkIndex', idx);

                        return uploadChunk(fd, uploadedBytes, file.size, onProgress).then(function () {
                            uploadedBytes += (end - start);
                            if (onProgress) onProgress(Math.round(uploadedBytes / file.size * 100));
                        });
                    });
                })(i);
            }

            // Step 3: 完成合并
            return chain.then(function () {
                return postJson(BASE + '/chunk/complete', { uploadId: uploadId });
            });
        });
    }

    function uploadChunk(fd, uploadedBytes, totalSize, onProgress) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', BASE + '/chunk/upload');

            var token = localStorage.getItem('token');
            if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);

            xhr.onload = function () {
                try {
                    var d = JSON.parse(xhr.responseText);
                    if (xhr.status === 200 && d.success) resolve(d);
                    else reject(new Error(d.error || '分片上传失败'));
                } catch (e) {
                    reject(new Error('响应解析失败'));
                }
            };

            xhr.onerror = function () { reject(new Error('网络错误')); };
            xhr.send(fd);
        });
    }

    // ======================== 工具 ========================

    function postJson(url, data) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', url);
            xhr.setRequestHeader('Content-Type', 'application/json');

            var token = localStorage.getItem('token');
            if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);

            xhr.onload = function () {
                try { resolve(JSON.parse(xhr.responseText)); }
                catch (e) { reject(new Error('响应解析失败')); }
            };
            xhr.onerror = function () { reject(new Error('网络错误')); };
            xhr.send(JSON.stringify(data));
        });
    }

    return {
        file: upload,
        SINGLE_LIMIT: SINGLE_LIMIT,
        CHUNK_SIZE: CHUNK_SIZE
    };
})();
