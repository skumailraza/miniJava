class AllocTest{
    public static void main(String[] args){
        int[][] ar;
        ar = new int[10][];
        ar[4] = new int[5];
        int[] a;
        a = ar[4];
        a[1] = 42;
        ar[4][2] = 123;
	    System.out.println(ar[4][1] + ar[4][2]);
    }
}
