package com.google.drive.googledrive.api;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.drive.googledrive.models.MoveFileQdo;
import com.google.drive.googledrive.models.UpdateFileQdo;
import com.google.drive.googledrive.service.GoogleDrive;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/drive")
public class DriveApi {

    @GetMapping("/files")
    public List<String> getFileList(@RequestParam() String fileType,
                                    @RequestParam(required = false) String filter) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<String> fileNames = new ArrayList<>();
//        List<String> fileIds = new ArrayList<>();
        String pageToken = null;
        do {
            Drive.Files.List list = driveService.files().list().setQ("mimeType='" + fileType + "'");
            if (filter != null && !filter.isEmpty()) {
                list.setQ("name contains '" + filter + "'");
            }
            FileList result = list.setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();
            for (File file : result.getFiles()) {
                fileNames.add(file.getName());
//                fileIds.add(file.getId());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
//        fileNames.addAll(fileIds);
        return fileNames;
    }

    @PutMapping("/files")
    public List<String> updateFiles(@RequestBody UpdateFileQdo updateFileQdo) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<File> files = getFiles(updateFileQdo.fileType, updateFileQdo.filter);
        files.parallelStream().forEach(file -> {
            file.setName(file.getName().replace(updateFileQdo.replaceFrom, updateFileQdo.replaceTo));
            try {
                driveService.files().update(file.getId(), file).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return files.stream().map(File::getName).collect(Collectors.toList());
    }

    @GetMapping("/folders")
    public List<String> getFolders() throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<String> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList result = driveService.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();
            for (File file : result.getFiles()) {
                files.add(file.getName() + "  " + file.getId());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
        return files;
    }

    @PostMapping("/folders")
    public String createFolder(@RequestParam String name) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File file = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();
        return "Folder created with name: " + name + " and id: " + file.getId();
    }

    @PutMapping("/move-files")
    public boolean moveFile(@RequestBody MoveFileQdo moveFileQdo) {
        Drive driveService = GoogleDrive.getDrive();
        // Retrieve the existing parents to remove
        for (String fileId : moveFileQdo.fileIds) {
            try {
                File file = driveService.files().get(fileId)
                        .setFields("parents")
                        .execute();
                StringBuilder previousParents = new StringBuilder();
                for (String parent : file.getParents()) {
                    previousParents.append(parent);
                    previousParents.append(',');
                }
                // Move the file to the new folder
                driveService.files().update(fileId, null)
                        .setAddParents(moveFileQdo.folderId)
                        .setRemoveParents(previousParents.toString())
                        .setFields("id, parents")
                        .execute();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Id: " + fileId);
                int index = moveFileQdo.fileIds.indexOf(fileId);
                System.out.println(moveFileQdo.fileIds.subList(index, moveFileQdo.fileIds.size()));
            }
        }
        return true;
    }

    @PutMapping("/move-files-fast")
    public List<String> moveFileFast(@RequestBody MoveFileQdo moveFileQdo) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<String> fileIds = getFileIds(moveFileQdo.fileType, moveFileQdo.filter);
        moveFileQdo.fileIds = fileIds;
        ForkJoinPool myPool = new ForkJoinPool(32);
        // Retrieve the existing parents to remove
        myPool.submit(() ->
                moveFileQdo.fileIds.parallelStream().forEach(fileId -> {
                    try {
                        File file = driveService.files().get(fileId)
                                .setFields("parents")
                                .execute();
                        StringBuilder previousParents = new StringBuilder();
                        for (String parent : file.getParents()) {
                            previousParents.append(parent);
                            previousParents.append(',');
                        }
                        // Move the file to the new folder
                        driveService.files().update(fileId, null)
                                .setAddParents(moveFileQdo.folderId)
                                .setRemoveParents(previousParents.toString())
                                .setFields("id, parents")
                                .execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("Id: " + fileId);
                        int index = moveFileQdo.fileIds.indexOf(fileId);
                        System.out.println(moveFileQdo.fileIds.subList(index, moveFileQdo.fileIds.size()));
                    }
                }));
        return fileIds;
    }

    public List<String> getFileIds(String fileType, String filter) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<String> fileIds = new ArrayList<>();
        String pageToken = null;
        do {
            Drive.Files.List list = driveService.files().list().setQ("mimeType='" + fileType + "'");
            if (filter != null && !filter.isEmpty()) {
                list.setQ("name contains '" + filter + "'");
            }
            FileList result = list.setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();
            for (File file : result.getFiles()) {
                fileIds.add(file.getId());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
        return fileIds;
    }

    public List<File> getFiles(String fileType, String filter) throws IOException {
        Drive driveService = GoogleDrive.getDrive();
        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            Drive.Files.List list = driveService.files().list().setQ("mimeType='" + fileType + "'");
            if (filter != null && !filter.isEmpty()) {
                list.setQ("name contains '" + filter + "'");
            }
            FileList result = list.setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute();
            files.addAll(result.getFiles());
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
        return files;
    }
}