# Chapter 01 ��ʶKafka

### 1.2.1 ��Ϣ������

��Ϣ(Message): 
* ���ݽṹ: �ֽ�����
* Ԫ����
    * ��(Key)
* ����(Batch): ��Ϣ�����α���������(Partition)
    * ����Խ��, ������Խ��, �ӳ�Խ��
* ģʽ(scheme): ������Ϣ�����Ե�

### 1.2.3 ���������

����Topic: Consumer��Message����ָ��topic
* ����(Partition): �߼��ṹΪ�ύ��־ (FIFO)
    * ���Է����ڲ�ͬ������������

### 1.2.4 ��������������

������(Producer): ����Message, ������ָ��Topic
* Message�������:
    * MessageĬ�ϱ����⵽��ͬ��Partition
    * �ɸ���Key��Partitioner����Hash���з���

������(Consumer): ����һ����Topic, ��FIFO˳������
* ƫ��(offset): �����ж�Message�Ƿ�����
* ������Ⱥ��(Consumer group): ��Ϊһ���߼�������, ����һ�����Consumerʵ��

### 1.2.5 broker�뼯Ⱥ

Broker: ΪConsumer�ṩ����, Ϊ��ȡPartition������������Ӧ, �Ǽ�Ⱥ����ɲ���
* ��Ⱥ������(Cluster controller) /Leader : 
    * �Զ���ѡ�ٳ���, 崻�������ѡ��
    * Э����Ⱥ������broker
    * ��Ϣ����(Replication)����: һ��broker��������һ��Partition, ���ұ�������Partition�ĸ���
    ![](replication-of-partitions-in-a-cluster.jpg)

������Ϣ:
* ��������:
    * ��һ��**��С**ʱ����
    * ��һ��**ʱ��**����

### 1.2.6 �༯Ⱥ

ʹ�ö༯Ⱥ��ԭ��:
* ������������
* ��ȫ�������
* ����������(����)

*Notice*: ��Ϣ����ֻ����һ����Ⱥ�ڽ���
* MirrorMaker: ��Ⱥ����Ϣ���ƵĹ���
    * ͨ��Producer��Consumerʵ��
    ![](multiple-datacenter-architecure.jpg)


## 1.4 ������̬ϵͳ

ʹ�ó���:
* �����: 
    1. �����û��, ��ҳ����ʴ���������
    2. ����Msg��Topic
    3. ��˽��д���, �����ѧϰ��
* ������Ϣ(��Ϣ�������): ͨ��Streamʵ��
    * ��ʽ����Ϣ
    * �ϲ���Ϣ
    * �������ô������ݺ���
* ϵͳ����ָʾ����־
    * ϵͳ���
    * ����־������ָ��Topic
* �ύ��־:
    * ͨ������(redo)���ָ�ϵͳ״̬
* ������: ʵʱ����������