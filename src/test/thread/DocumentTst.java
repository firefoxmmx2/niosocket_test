package test.thread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Created by hooxin on 15-3-24.
 */
public class DocumentTst {
    public static void main(String[] args) throws IOException {
        WordCounter wordCounter=new WordCounter();
        Folder folder=Folder.fromDirectory(new File(args[0]));
        System.out.println(wordCounter.countOccurrencesInParallel(folder,args[1]));
    }
}

class Document {
    private final List<String> lines;

    public Document(List<String> lines) {
        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    static Document fromFile(File file) throws IOException {
        List<String> lines=new LinkedList<String>();
        try(BufferedReader reader=new BufferedReader(new FileReader(file))) {
            String line= reader.readLine();
            while (line !=null) {
                lines.add(line);
                line= reader.readLine();
            }
        }
        return new Document(lines);
    }
}

class Folder {
    private final List<Folder> subForders;
    private final List<Document> documents;

    public Folder(List<Folder> subForders, List<Document> documents) {
        this.subForders = subForders;
        this.documents = documents;
    }

    public List<Folder> getSubForders() {
        return subForders;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    static Folder fromDirectory(File dir) throws IOException{
        List<Document> documents = new LinkedList<>();
        List<Folder> subFolders=new LinkedList<>();
        for (File entry : dir.listFiles()) {
            if(entry.isDirectory()){
                subFolders.add(Folder.fromDirectory(entry));
            }
            else {
                documents.add(Document.fromFile(entry));
            }
        }
        return new Folder(subFolders,documents);
    }
}

class WordCounter {
    String[] wordsIn(String line){
        return line.trim().split("(\\s|\\p{Punct})+");
    }

    Long occurrencesCount(Document document,String searchedWord) {
        long count=0;
        for (String line : document.getLines()) {
            for (String word : wordsIn(line)) {
                if(searchedWord.equals(word)){
                    count+=1;
                }
            }
        }
        return count;
    }

    class DocumentSearchTask extends RecursiveTask<Long>{
        private final Document document;
        private final String searchedWord;

        public DocumentSearchTask(Document document, String searchedWord) {
            super();
            this.document = document;
            this.searchedWord = searchedWord;
        }

        @Override
        protected Long compute() {
            return occurrencesCount(document,searchedWord);
        }
    }

    class FolderSearchTask extends RecursiveTask<Long>{
        private final Folder folder;
        private final String seachedWord;

        public FolderSearchTask(Folder folder, String seachedWord) {
            super();
            this.folder = folder;
            this.seachedWord = seachedWord;
        }

        @Override
        protected Long compute() {
            long count=0l;
            List<RecursiveTask<Long>> forks=new LinkedList<>();
            for (Folder subFolder : folder.getSubForders()) {
                FolderSearchTask task=new FolderSearchTask(subFolder,seachedWord);
                forks.add(task);
                task.fork();
            }
            for (Document document : folder.getDocuments()) {
                DocumentSearchTask task=new DocumentSearchTask(document,seachedWord);
                forks.add(task);
                task.fork();
            }
            for (RecursiveTask<Long> task : forks) {
                count+=task.join();
            }
            return count;
        }
    }
    private final ForkJoinPool forkJoinPool=new ForkJoinPool();
    Long countOccurrencesInParallel(Folder folder,String searchedWord) {
        return forkJoinPool.invoke(new FolderSearchTask(folder,searchedWord));
    }

    Long countOccurrencesOnSingleThread(Folder folder, String searchedWord) {
        long count = 0;
        for (Folder subFolder : folder.getSubForders()) {
            count = count + countOccurrencesOnSingleThread(subFolder, searchedWord);
        }
        for (Document document : folder.getDocuments()) {
            count = count + occurrencesCount(document, searchedWord);
        }
        return count;
    }
}
